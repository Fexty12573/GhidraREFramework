import json
import sys

a = json.load(open(sys.argv[1], 'r', encoding='utf8'))
json.dump(a, open(sys.argv[1] + ".compact.json", "w", encoding='utf8'), indent=None)