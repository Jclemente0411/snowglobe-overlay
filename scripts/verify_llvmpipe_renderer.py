"""Require the actual GL renderer identity, not a capability or extension name."""
import argparse
import re
from pathlib import Path


def verify(text, source):
    label = 'OpenGL renderer string:' if source == 'host' else 'GLES:'
    identities = re.findall(r'^[ \t]*' + re.escape(label) + r'[ \t]*([^\r\n]+)\r?$',
                            text, flags=re.MULTILINE | re.IGNORECASE)
    if not identities:
        raise ValueError('Missing ' + source + ' OpenGL renderer identity')
    for identity in identities:
        if not re.search(r'\bllvmpipe\b', identity, flags=re.IGNORECASE):
            raise ValueError('Expected llvmpipe in ' + source + ' renderer: ' + identity)
        if re.search(r'\b(?:swiftshader|angle|swangle|lavapipe|zink)\b', identity, flags=re.IGNORECASE):
            raise ValueError('Unexpected fallback in ' + source + ' renderer: ' + identity)
    print('Verified ' + source + ' llvmpipe renderer: ' + ' | '.join(identities))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', choices=('host', 'device'), required=True)
    parser.add_argument('report', type=Path)
    args = parser.parse_args()
    try:
        verify(args.report.read_text(), args.source)
    except (OSError, ValueError) as error:
        parser.exit(1, 'OpenGL renderer verification failed: ' + str(error) + '\n')


if __name__ == '__main__':
    main()
