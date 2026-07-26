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

Beroenden:
    pip install geopandas fiona

Steg:
    1) Inspektera vilka kolumner filen faktiskt har:
         python convert_rastplatser.py inspect rastplats.gpkg

    2) Justera FIELD_MAP nedan så den matchar (vänster = appens fältnamn,
       höger = kolumnnamnet i er faktiska fil).

    3) Konvertera:
         python convert_rastplatser.py convert rastplats.gpkg rastplatser.json

    4) Kopiera resultatet till:
         app/src/main/assets/rastplatser.json
"""

import json
import sys

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


def inspect(path: str) -> None:
    import geopandas as gpd

    gdf = gpd.read_file(path)
    print(f"Antal rader: {len(gdf)}")
    print("Kolumner i filen:")
    for col in gdf.columns:
        sample = gdf[col].dropna().iloc[0] if gdf[col].notna().any() else None
        print(f"  - {col}  (exempel: {sample!r})")


def convert(input_path: str, output_path: str) -> None:
    import geopandas as gpd

    gdf = gpd.read_file(input_path)

    # Rastplatser lagras normalt som punkter. Om filen istället har
    # linje-/ytgeometri (t.ex. hela anläggningsområdet), räkna ut en
    # representativ punkt.
    gdf["_lat"] = gdf.geometry.centroid.y
    gdf["_lon"] = gdf.geometry.centroid.x

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
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(1)

    command, path = sys.argv[1], sys.argv[2]
    if command == "inspect":
        inspect(path)
    elif command == "convert":
        if len(sys.argv) < 4:
            print("Ange output-fil: convert_rastplatser.py convert <in.gpkg> <out.json>")
            sys.exit(1)
        convert(path, sys.argv[3])
    else:
        print(f"Okänt kommando: {command}")
        sys.exit(1)
