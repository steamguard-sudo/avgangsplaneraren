"""
convert_rastplatser.py

Konverterar Trafikverkets GeoPackage-export av dataprodukten "Rastplats"
(hämtad via Datautbytesportalen eller Lastkajen) till det JSON-format som
appen förväntar sig i app/src/main/assets/rastplatser.json.

Läser GeoPackage-filen DIREKT via Pythons inbyggda sqlite3-modul (en
GeoPackage är tekniskt sett bara en SQLite-databas med några standard-
tabeller för geometri/metadata). Detta kräver INGEN separat GDAL-
installation och undviker ett känt fel i pyogrio/fiona som kan uppstå på
stora exporter med blandade/ovanliga geometrityper.

VIKTIGT — läs innan du kör:
Det här skriptet vet INTE på förhand exakt vilka kolumnnamn Trafikverkets
GeoPackage-fil använder för utrustning (bord, bänk, toalett osv.) — det
varierar mellan dataproduktversioner och kan ha ändrats sedan det här
skrevs. Kör alltid steg 1–2 nedan först och stäm av `FIELD_MAP` mot det
som faktiskt finns i filen, annars blir bord/bänk/toalett-fälten fel eller
tomma.

En GeoPackage-fil (.gpkg) kan innehålla FLERA LAGER (t.ex. om ni beställt
en stor export med flera dataprodukter i samma fil — vanligt vid en
"hela Sverige"-beställning). Kör alltid `layers`-kommandot först för att
se vilka lager som finns, och peka ut rätt lager med --layer om det behövs.

Beroenden (obs: varken geopandas, pyogrio, fiona eller GDAL krävs):
    pip install shapely pyproj

Steg:
    1) Se vilka LAGER filen innehåller:
         python convert_rastplatser.py layers rastplats.gpkg

    2) Inspektera kolumnerna i rätt lager:
         python convert_rastplatser.py inspect rastplats.gpkg --layer Rastplats

    3) Justera FIELD_MAP nedan så den matchar (vänster = appens fältnamn,
       höger = kolumnnamnet i er faktiska fil).

    4) Konvertera:
         python convert_rastplatser.py convert rastplats.gpkg rastplatser.json --layer Rastplats

    5) Kopiera resultatet till:
         app/src/main/assets/rastplatser.json
"""

import json
import sqlite3
import struct
import sys
import argparse

# Justera höger sida efter vad `inspect` visar för er fil.
# Sätt till None för fält som saknas i er export — då blir värdet False/null.
FIELD_MAP = {
    "namn": "NAMN",
    "vagnummer": "VAGNUMMER",
    "bord": "BORD",  # t.ex. 1/0, "Ja"/"Nej", eller True/False beroende på export
    "bank": "BANK",
    "toalett": "TOALETT",
    "soptunna": "SOPTUNNA",
    "handikappanpassad": "HANDIKAPPANPASSAD",
}


def truthy(value) -> bool:
    """Tolkar olika sätt att skriva 'ja' i öppna dataset som True."""
    if value is None:
        return False
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, float)):
        return value != 0
    text = str(value).strip().lower()
    return text in {"ja", "yes", "true", "1", "x"}


def _first_features_layer(con: sqlite3.Connection) -> str:
    row = con.execute(
        "SELECT table_name FROM gpkg_contents WHERE data_type = 'features' LIMIT 1"
    ).fetchone()
    if not row:
        raise SystemExit(
            "Hittade inget lager med data_type='features' i filen. "
            "Kör 'layers'-kommandot och ange rätt lager manuellt med --layer."
        )
    return row[0]


def _geometry_column(con: sqlite3.Connection, table: str):
    row = con.execute(
        "SELECT column_name, srs_id FROM gpkg_geometry_columns WHERE table_name = ?",
        (table,),
    ).fetchone()
    if not row:
        return None, None
    return row[0], row[1]


def _decode_geometry(blob):
    """
    Avkodar en GeoPackage-geometri-blob (GPB-format: en liten header följt
    av standard-WKB) till en shapely-geometri. Returnerar (srs_id, geometri).
    Se specifikationen: https://www.geopackage.org/spec/#gpb_format
    """
    if blob is None:
        return None, None

    from shapely import wkb as shapely_wkb

    if blob[0:2] != b"GP":
        # Inte en GeoPackage-header — anta att det redan är rå WKB.
        return None, shapely_wkb.loads(bytes(blob))

    flags = blob[3]
    byte_order = "<" if (flags & 0x01) else ">"
    srs_id = struct.unpack(byte_order + "i", blob[4:8])[0]

    envelope_indicator = (flags >> 1) & 0x07
    envelope_sizes = {0: 0, 1: 32, 2: 48, 3: 48, 4: 64}
    env_size = envelope_sizes.get(envelope_indicator, 0)
    header_len = 8 + env_size

    empty_flag = (flags >> 4) & 0x01
    if empty_flag:
        return srs_id, None

    geom = shapely_wkb.loads(bytes(blob[header_len:]))
    return srs_id, geom


def _crs_for_srs_id(con: sqlite3.Connection, srs_id: int):
    import pyproj

    if srs_id in (0, -1):
        return None

    row = con.execute(
        "SELECT organization, organization_coordsys_id, definition "
        "FROM gpkg_spatial_ref_sys WHERE srs_id = ?",
        (srs_id,),
    ).fetchone()

    if row:
        organization, org_coordsys_id, definition = row
        if definition and definition.strip().upper() not in ("", "UNDEFINED"):
            try:
                return pyproj.CRS.from_user_input(definition)
            except Exception:
                pass
        if organization and organization.upper() == "EPSG" and org_coordsys_id:
            try:
                return pyproj.CRS.from_epsg(org_coordsys_id)
            except Exception:
                pass

    try:
        return pyproj.CRS.from_epsg(srs_id)
    except Exception:
        return None


def list_layers(path: str) -> None:
    con = sqlite3.connect(path)
    try:
        rows = con.execute(
            "SELECT table_name, data_type, identifier FROM gpkg_contents ORDER BY table_name"
        ).fetchall()
    finally:
        con.close()

    print(f"Filen innehåller {len(rows)} lager:")
    for table_name, data_type, identifier in rows:
        extra = f"  — {identifier}" if identifier and identifier != table_name else ""
        print(f"  - {table_name}  [{data_type}]{extra}")
    print()
    print("Leta efter ett namn som innehåller 'rast' eller liknande (t.ex. 'Rastplats',")
    print("'NVDB_DKRastplats', 'DK_Rastplats' — namnet kan variera). Kör sedan:")
    print(f"  python {sys.argv[0]} inspect {path} --layer <lagernamn>")


def inspect(path: str, layer: str | None = None) -> None:
    con = sqlite3.connect(path)
    try:
        table = layer or _first_features_layer(con)
        count = con.execute(f'SELECT COUNT(*) FROM "{table}"').fetchone()[0]
        cur = con.execute(f'SELECT * FROM "{table}" LIMIT 1')
        col_names = [d[0] for d in cur.description]
        sample = cur.fetchone() or [None] * len(col_names)

        print(f"Lager: {table}")
        print(f"Antal rader: {count}")
        print("Kolumner i filen:")
        for name, value in zip(col_names, sample):
            if isinstance(value, (bytes, bytearray)):
                display = f"<geometri, {len(value)} bytes>"
            else:
                display = repr(value)
            print(f"  - {name}  (exempel: {display})")
    finally:
        con.close()


def convert(input_path: str, output_path: str, layer: str | None = None) -> None:
    con = sqlite3.connect(input_path)
    result = []
    skipped_no_geometry = 0
    try:
        table = layer or _first_features_layer(con)
        geom_col, declared_srs = _geometry_column(con, table)

        cur = con.execute(f'SELECT * FROM "{table}"')
        col_names = [d[0] for d in cur.description]

        transformer_cache: dict[int, object] = {}

        for i, row in enumerate(cur):
            row_dict = dict(zip(col_names, row))
            blob = row_dict.get(geom_col) if geom_col else None
            srs_id, geom = _decode_geometry(blob)
            srs_id = srs_id if srs_id is not None else declared_srs

            if geom is None or geom.is_empty:
                skipped_no_geometry += 1
                continue

            if geom.geom_type == "Point":
                x, y = geom.x, geom.y
            else:
                centroid = geom.centroid
                x, y = centroid.x, centroid.y

            if srs_id and srs_id != 4326:
                if srs_id not in transformer_cache:
                    crs = _crs_for_srs_id(con, srs_id)
                    if crs is not None:
                        import pyproj
                        transformer_cache[srs_id] = pyproj.Transformer.from_crs(
                            crs, "EPSG:4326", always_xy=True
                        )
                    else:
                        transformer_cache[srs_id] = None
                transformer = transformer_cache[srs_id]
                if transformer is not None:
                    x, y = transformer.transform(x, y)

            def field(key):
                col = FIELD_MAP.get(key)
                return row_dict.get(col) if col else None

            feature_id = row_dict.get("OBJECTID") or row_dict.get("fid") or i

            result.append({
                "id": str(feature_id),
                "namn": (str(field("namn")) if field("namn") else None),
                "latitud": float(y),
                "longitud": float(x),
                "vagnummer": (str(field("vagnummer")) if field("vagnummer") else None),
                "harBord": truthy(field("bord")),
                "harBank": truthy(field("bank")),
                "harToalett": truthy(field("toalett")),
                "harSoptunna": truthy(field("soptunna")),
                "handikappanpassad": truthy(field("handikappanpassad")),
            })
    finally:
        con.close()

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)

    print(f"Skrev {len(result)} rastplatser till {output_path}")
    if skipped_no_geometry:
        print(f"({skipped_no_geometry} rader saknade geometri och hoppades över)")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    subparsers = parser.add_subparsers(dest="command", required=True)

    p_layers = subparsers.add_parser("layers", help="Lista alla lager i filen")
    p_layers.add_argument("path", help="Sökväg till .gpkg-filen")

    p_inspect = subparsers.add_parser("inspect", help="Visa kolumnerna i ett lager")
    p_inspect.add_argument("path", help="Sökväg till .gpkg-filen")
    p_inspect.add_argument("--layer", default=None, help="Lagernamn (se 'layers'-kommandot)")

    p_convert = subparsers.add_parser("convert", help="Konvertera till appens JSON-format")
    p_convert.add_argument("input", help="Sökväg till .gpkg-filen")
    p_convert.add_argument("output", help="Sökväg till JSON-filen som ska skapas")
    p_convert.add_argument("--layer", default=None, help="Lagernamn (se 'layers'-kommandot)")

    args = parser.parse_args()

    if args.command == "layers":
        list_layers(args.path)
    elif args.command == "inspect":
        inspect(args.path, layer=args.layer)
    elif args.command == "convert":
        convert(args.input, args.output, layer=args.layer)
