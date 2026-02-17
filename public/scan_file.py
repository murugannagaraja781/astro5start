
import sys
import re

filepath = '/Users/wohozo/Documents/astro5start/public/desktop-view.html'

try:
    with open(filepath, 'r') as f:
        content = f.read()
except FileNotFoundError:
    print(f"File not found: {filepath}")
    sys.exit(1)

def check_balance(text, start_line_offset, block_name):
    # Remove comments
    # CSS comments /* ... */
    text_no_comments = re.sub(r'/\*.*?\*/', lambda m: ' ' * len(m.group(0)), text, flags=re.DOTALL)

    # JS comments // ... (only if it's a script block, but harmless in CSS usually unless URL)
    # Actually, simplistic removal might be dangerous if we mix languages.
    # Let's just do brace counting on the raw text for now, but handle strings?
    # Handling mixed content properly is hard with regex.
    # Let's try to be smart about strings.

    # Generic string remover for " and ' and ` (template literals)
    # This is a naive tokenizer

    i = 0
    length = len(text)
    balance = 0
    stack = []

    # We need to track line numbers relative to start_line_offset
    current_line = 0
    current_col = 0

    # Map from char index to (line, col)
    # Precompute line starts
    line_starts = [0]
    for idx, char in enumerate(text):
        if char == '\n':
            line_starts.append(idx + 1)

    def get_pos(idx):
        # find line
        # simple binary search or linear scan
        # linear scan is fine
        line_idx = 0
        for li, start in enumerate(line_starts):
            if start > idx:
                break
            line_idx = li

        col = idx - line_starts[line_idx] + 1
        return (start_line_offset + line_idx + 1, col)

    while i < length:
        char = text[i]

        # Skip comments
        if i + 1 < length and char == '/' and text[i+1] == '*':
            # /* comment */
            i += 2
            while i + 1 < length and not (text[i] == '*' and text[i+1] == '/'):
                i += 1
            i += 2
            continue

        if i + 1 < length and char == '/' and text[i+1] == '/':
            # // comment
            i += 2
            while i < length and text[i] != '\n':
                i += 1
            continue

        # Strings
        if char == '"' or char == "'" or char == '`':
            quote = char
            i += 1
            while i < length:
                if text[i] == '\\':
                    i += 2
                    continue
                if text[i] == quote:
                    i += 1
                    break
                i += 1
            continue

        if char == '{':
            pos = get_pos(i)
            stack.append(pos)
            balance += 1
        elif char == '}':
            if balance > 0:
                stack.pop()
                balance -= 1
            else:
                pos = get_pos(i)
                print(f"[{block_name}] Extra closing brace at Line {pos[0]}")

        i += 1

    if balance > 0:
        last = stack[-1]
        print(f"[{block_name}] Unclosed brace opened at Line {last[0]}")
        return False
    return True

# Find all blocks
print("Checking blocks...")

# Regex for style tags
style_pattern = re.compile(r'<style[^>]*>(.*?)</style>', re.DOTALL | re.IGNORECASE)
for match in style_pattern.finditer(content):
    start_pos = match.start(1)
    block_content = match.group(1)

    # Calculate start line number
    pre_content = content[:start_pos]
    start_line = pre_content.count('\n')

    check_balance(block_content, start_line, "STYLE")

# Regex for script tags
script_pattern = re.compile(r'<script[^>]*>(.*?)</script>', re.DOTALL | re.IGNORECASE)
for match in script_pattern.finditer(content):
    start_pos = match.start(1)
    block_content = match.group(1)

    pre_content = content[:start_pos]
    start_line = pre_content.count('\n')

    check_balance(block_content, start_line, "SCRIPT")

print("Check complete.")
