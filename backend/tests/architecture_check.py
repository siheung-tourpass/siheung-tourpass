"""Guard the source boundaries of the layered monolith and MVC application."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'backend/src/main'
SQL = re.compile(r'"\s*(?:SELECT\s|INSERT\s+INTO\s|UPDATE\s|DELETE\s+FROM\s)', re.I)
JDBC = re.compile(r'\.(?:prepareStatement|createStatement|executeQuery|executeUpdate)\s*\(')


def sources(layer):
    return sorted(p for language in ('java', 'kotlin')
                  for p in (SOURCE / language / 'kr/ac/siheung/tourpass' / layer).rglob('*')
                  if p.suffix in ('.java', '.kt'))


def check():
    errors = []
    for layer in ('controller', 'filter', 'service', 'dao'):
        paths = sources(layer)
        assert paths, f'No {layer} sources found'
        for path in paths:
            source = path.read_text()
            label = path.relative_to(ROOT)
            if layer in ('controller', 'filter', 'service') and (SQL.search(source) or JDBC.search(source)):
                errors.append(f'{label}: SQL execution belongs in DAO')
            if layer in ('controller', 'filter') and re.search(r'\bdao\.|\b\w+Dao\b', source):
                errors.append(f'{label}: presentation must call service, not DAO')
            if layer == 'service' and re.search(r'\bSql\b|jakarta\.servlet|tourpass\.(?:controller|filter)\b', source):
                errors.append(f'{label}: service must use DAO and stay independent of HTTP')
            if layer == 'dao' and re.search(r'tourpass\.(?:controller|filter|service)\b|jakarta\.servlet', source):
                errors.append(f'{label}: DAO must stay independent of service and HTTP')
    views = sorted((ROOT / 'frontend/WEB-INF/views').glob('*.jsp*'))
    assert views, 'No JSP views found'
    for path in views:
        if re.search(r'<%(?![@-])|<sql:|kr\.ac\.siheung\.tourpass|java\.sql', path.read_text()):
            errors.append(f'{path.relative_to(ROOT)}: view must not contain Java or SQL tags')
    assert not errors, '\n'.join(errors)


if __name__ == '__main__':
    check()
    print('PASS: layered monolith / MVC source boundaries')
