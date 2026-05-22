import json
import sys

try:
    with open(r"C:\Users\99910544\AndroidStudioProjects\aineistot\valmiit\kala_kartta_kaikki_21_05_2026.json", 'r', encoding='utf-8') as f:
        json.load(f)
    print("JSON is valid UTF-8")
except Exception as e:
    print(f"Error: {e}")
