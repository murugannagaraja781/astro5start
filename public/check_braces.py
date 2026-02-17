
import sys

filepath = '/Users/wohozo/Documents/astro5start/public/desktop-view.html'
start_line = 7466 # 0-indexed, so line 7467

try:
    with open(filepath, 'r') as f:
        lines = f.readlines()
except FileNotFoundError:
    print(f"File not found: {filepath}")
    sys.exit(1)

# Get the content from start_line onwards
style_content_lines = lines[start_line:]

# Find where </style> is
end_index = -1
for i, line in enumerate(style_content_lines):
    if '</style>' in line:
        end_index = i
        break

if end_index != -1:
    css_lines = style_content_lines[:end_index]
    full_css = ''.join(css_lines)

    open_braces = 0
    balance = 0
    stack = []

    current_char_idx = 0

    for line_idx, line in enumerate(css_lines):
        for char_idx, char in enumerate(line):
            if char == '{':
                stack.append((start_line + line_idx + 1, char_idx + 1))
                balance += 1
            elif char == '}':
                if balance > 0:
                    stack.pop()
                    balance -= 1
                else:
                    print(f"Extra closing brace found at line {start_line + line_idx + 1}, column {char_idx + 1}")

    if balance > 0:
        print(f"Missing {balance} closing braces.")
        print(f"Last unclosed brace opened at line {stack[-1][0]}, column {stack[-1][1]}")
    elif balance == 0:
        print("Braces are balanced within the style block.")

else:
    print("Could not find </style> tag after line 7467")
