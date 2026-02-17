
import sys
import re

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

    # Remove contents of comments /* ... */
    # Note: this simple regex doesn't handle nested comments (which aren't valid in CSS anyway)
    # But newlines inside comments need to be preserved to keep line numbers correct?
    # Or replace comment content with spaces.

    def replace_comment(match):
        return ' ' * len(match.group(0))

    # Pattern for comments: /\*.*?\*/ (dot matches newline with DOTALL)
    full_css_no_comments = re.sub(r'/\*.*?\*/', replace_comment, full_css, flags=re.DOTALL)

    # Remove contents of strings "..." and '...'
    # This is tricky because strings can contain escaped quotes.
    # Simple regex for strings: "([^"\\]|\\.)*" or '([^'\\]|\\.)*'

    def replace_string(match):
        return '""' # Replace string with empty string constant to avoid counting braces inside

    full_css_clean = re.sub(r'"([^"\\]|\\.)*"|\'([^\'\\]|\\.)*\'', replace_string, full_css_no_comments)

    # Now counting braces
    open_braces = 0
    stack = []

    # We want line numbers, so let's reconstruct line numbers.
    # We can iterate over full_css_clean char by char, and count newlines.

    current_line = start_line + 1
    current_col = 1

    for i, char in enumerate(full_css_clean):
        if char == '\n':
            current_line += 1
            current_col = 1
        else:
            if char == '{':
                stack.append((current_line, current_col))
                open_braces += 1
            elif char == '}':
                if open_braces > 0:
                    stack.pop()
                    open_braces -= 1
                else:
                    print(f"Extra closing brace found at line {current_line}, column {current_col}")
            current_col += 1

    if open_braces > 0:
        print(f"Missing {open_braces} closing braces.")
        print(f"Last unclosed brace opened at line {stack[-1][0]}, column {stack[-1][1]}")
    elif open_braces == 0:
        print("Braces are balanced (ignoring comments/strings).")

else:
    print("Could not find </style> tag after line 7467")
