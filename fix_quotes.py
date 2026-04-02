import re, glob, os
res_dir = 'app/src/main/res'
xml_files = glob.glob(os.path.join(res_dir, 'values-*/strings.xml'))

def escape_quotes(match, tag):
    text = match.group(1)
    text_fixed = text.replace(r"\'", "'").replace("'", r"\'")
    return f'<string name="{tag}">{text_fixed}</string>'

count = 0
for file in xml_files:
    with open(file, 'r', encoding='utf-8') as f:
        content = f.read()

    original = content
    for tag in ['unlock_amoled_theme', 'unlock_amoled_theme_desc', 'watch_ad', 'ad_not_ready']:
        pattern = f'<string name="{tag}">(.*?)</string>'
        content = re.sub(pattern, lambda m, t=tag: escape_quotes(m, t), content)
    
    if content != original:
        with open(file, 'w', encoding='utf-8') as f:
            f.write(content)
        count += 1

print(f"Fixed quotes in {count} language files!")
