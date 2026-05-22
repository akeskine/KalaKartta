import json

def test_import():
    path = r"C:\Users\99910544\AndroidStudioProjects\aineistot\valmiit\kala_kartta_kaikki_21_05_2026.json"
    with open(path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    print(f"Total objects in JSON: {len(data)}")
    
    for i, obj in enumerate(data):
        try:
            # required
            species = obj['species']
            lat = float(obj['latitude'])
            lon = float(obj['longitude'])
            
            # opt
            weight = int(obj.get('weight', 0) or 0)
            length = int(obj.get('length', 0) or 0)
            method = str(obj.get('method', ''))
            strikeDepth = float(obj.get('strikeDepth', 0.0) or 0.0)
            # ... and so on
        except Exception as e:
            print(f"Error at index {i}, ID {obj.get('id')}: {e}")
            return
            
    print("All objects parsed successfully with basic float/int/str conversions.")

test_import()
