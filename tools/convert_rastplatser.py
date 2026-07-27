"""
convert_rastplatser.py

Konverterar Trafikverkets GeoPackage-export av dataprodukten "Rastplats"
(hämtad via Datautbytesportalen eller Lastkajen) till det JSON-format som
appen förväntar sig i app/src/main/assets/rastplatser.json.

VIKTIGT — läs innan du kör:
Det här skriptet vet INTE på förhand exakt vilka kolumnnamn Trafikverkets
GeoPackage-fil använder för utrustning (bord, bänk, toalett osv.) — det
varierar mellan dataproduktversioner och kan ha ändrats sedan det här
skrevs. Kör alltid steg 1 nedan först och stäm av `FIELD_MAP` mot det som
faktiskt finns i filen, annars blir bord/bänk/toalett-fälten fel eller tomma.

En GeoPackage-fil (.gpkg) kan innehålla FLERA LAGER (t.ex. om ni beställt
en stor export med flera dataprodukter i samma fil — vanligt vid en
"hela Sverige"-beställning). Kör alltid `layers`-kommandot först för att
se vilka lager som finns, och peka ut rätt lager med --layer om det behövs.

Beroenden:
    pip install geopandas pyogrio

Steg:
    1) Se vilka LAGER filen innehåller (viktigt för stora/kombinerade beställningar):
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


def list_layers(path: str) -> None:
    import pyogrio

    layers = pyogrio.list_layers(path)
    # pyogrio returnerar en array av [namn, geometrityp] per lager
    print(f"Filen innehåller {len(layers)} lager:")
    for name, geom_type in layers:
        print(f"  - {name}  ({geom_type})")
    print()
    print("Leta efter ett namn som innehåller 'rast' eller liknande (t.ex. 'Rastplats',")
    print("'NVDB_DKRastplats', 'DK_Rastplats' — namnet kan variera). Kör sedan:")
    print(f"  python {sys.argv[0]} inspect {path} --layer <lagernamn>")


def inspect(path: str, layer: str | None = None) -> None:
    import geopandas as gpd

    gdf = gpd.read_file(path, layer=layer, engine="pyogrio")
    print(f"Lager: {layer or '(första/enda lagret)'}")
    print(f"Antal rader: {len(gdf)}")
    print("Kolumner i filen:")
    for col in gdf.columns:
        sample = gdf[col].dropna().iloc[0] if gdf[col].notna().any() else None
        print(f"  - {col}  (exempel: {sample!r})")


def convert(input_path: str, output_path: str, layer: str | None = None) -> None:
    import geopandas as gpd

    gdf = gpd.read_file(input_path, layer=layer, engine="pyogrio")

    # Rastplatser lagras normalt som punkter — då är lat/lon exakta.
    # Om geometrin istället är en linje/yta (t.ex. hela anläggningsområdet),
    # projicera till SWEREF99 TM (Sveriges standardprojektion, meter-baserad)
    # innan centroid beräknas, annars blir en gradbaserad centroid missvisande.
    if (gdf.geometry.geom_type == "Point").all():
        gdf["_lat"] = gdf.geometry.y
        gdf["_lon"] = gdf.geometry.x
    else:
        projected = gdf.geometry.to_crs(epsg=3006).centroid
        centroid_wgs84 = projected.to_crs(epsg=4326)
        gdf["_lat"] = centroid_wgs84.y
        gdf["_lon"] = centroid_wgs84.x

    result = []
    for i, row in gdf.iterrows():
        def field(key):
            col = FIELD_MAP.get(key)
            return row[col] if col and col in row else None

        result.append({
            "id": str(row.get("OBJECTID", i)),
            "namn": (str(field("namn")) if field("namn") else None),
            "latitud": float(row["_lat"]),
            "longitud": float(row["_lon"]),
            "vagnummer": (str(field("vagnummer")) if field("vagnummer") else None),
            "harBord": truthy(field("bord")),
            "harBank": truthy(field("bank")),
            "harToalett": truthy(field("toalett")),
            "harSoptunna": truthy(field("soptunna")),
            "handikappanpassad": truthy(field("handikappanpassad")),
        })

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)

    print(f"Skrev {len(result)} rastplatser till {output_path}")


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
