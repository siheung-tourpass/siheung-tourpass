"""Check generated imports without a running PostgreSQL server."""
import json
import sqlite3
import subprocess
import sys
from datetime import date
from pathlib import Path

root = Path(__file__).resolve().parents[1]
data = json.loads((root / 'data/siheung-tourism.json').read_text())
sources = {s['code']: s for s in data['sources']}
for key in ('sources', 'regions', 'themes', 'companion_types', 'places', 'annotations'):
    assert len({r['code'] for r in data[key]}) == len(data[key]), key
for place in data['places']:
    assert place['review_status'] == 'DRAFT' and place['reviewed_by'] is None and place['reviewed_at'] is None
    assert place['material_kind'] == 'REAL' and not place['merchant_links'] and not place['benefit_links']
    assert all(s in sources for s in place['source_ids']) and place['source_ids']
    assert date.fromisoformat(place['checked_on']) <= date.fromisoformat(data['collected_on'])
    locations = [a['object'] for a in data['annotations'] if a['subject'] == place['code'] and a['predicate'] == 'locatedIn']
    assert locations == [place['region_code']]
for annotation in data['annotations']:
    assert all(s in sources for s in annotation['source_ids']) and annotation['source_ids']
    assert annotation['review_status'] == 'DRAFT'

for replace in (False, True):
    command = [sys.executable, str(root / 'scripts/tourism-sql.py')]
    sql = subprocess.check_output(command + (['--replace-demo'] if replace else []), text=True)
    db = sqlite3.connect(':memory:')
    db.executescript('''
        PRAGMA foreign_keys=ON;
        CREATE TABLE region(id INTEGER PRIMARY KEY, code TEXT UNIQUE, name TEXT, active BOOLEAN DEFAULT TRUE);
        CREATE TABLE theme(id INTEGER PRIMARY KEY, code TEXT UNIQUE, name TEXT);
        CREATE TABLE companion_type(id INTEGER PRIMARY KEY, code TEXT UNIQUE, name TEXT);
        CREATE TABLE place(id INTEGER PRIMARY KEY, code TEXT UNIQUE, region_id INTEGER REFERENCES region(id),
            name TEXT, description TEXT, location_description TEXT, material_kind TEXT,
            source_url TEXT, checked_on TEXT, evidence_note TEXT, review_status TEXT DEFAULT 'DRAFT',
            reviewed_by INTEGER, reviewed_at TEXT, active BOOLEAN DEFAULT TRUE);
        CREATE TABLE place_source(place_id INTEGER REFERENCES place(id), source_url TEXT, checked_on TEXT, evidence_note TEXT);
        CREATE TABLE place_theme(place_id INTEGER REFERENCES place(id), theme_id INTEGER REFERENCES theme(id),
            material_kind TEXT, source_url TEXT, checked_on TEXT, evidence_note TEXT, review_status TEXT DEFAULT 'DRAFT',
            PRIMARY KEY(place_id,theme_id));
        CREATE TABLE place_companion(place_id INTEGER REFERENCES place(id), companion_type_id INTEGER REFERENCES companion_type(id),
            material_kind TEXT, source_url TEXT, checked_on TEXT, evidence_note TEXT, review_status TEXT DEFAULT 'DRAFT',
            PRIMARY KEY(place_id,companion_type_id));
        CREATE TABLE ontology_revision(id INTEGER PRIMARY KEY, revision INTEGER);
        INSERT INTO ontology_revision VALUES(1,1);
        INSERT INTO region(code,name) VALUES('REGION_EMPTY','시연 빈 지역');
        INSERT INTO region(code,name) VALUES('REGION_GEOBUK','거북섬');
        INSERT INTO place(code,region_id,material_kind,review_status) VALUES('PLACE_A',2,'DEMO','APPROVED');
    ''')
    # SQLite also supports the generated ON CONFLICT syntax.
    translated = sql.replace("SET TIME ZONE 'UTC'; ", '')
    db.executescript(translated)
    sources = {s['code']: s for s in data['sources']}
    for place in data['places']:
        row = db.execute('SELECT location_description,source_url,checked_on,review_status,reviewed_by,reviewed_at FROM place WHERE code=?', (place['code'],)).fetchone()
        assert row[1:] == (sources[place['source_ids'][0]]['url'], place['checked_on'], 'DRAFT', None, None)
        if place['address_status'] == 'SOURCE_RECORDED' and place['address']:
            assert row[0] == place['address']
        else:
            assert row[0] == place['location_note'] + ' · 상세 주소 미확인'
    for predicate, table in [('hasTheme', 'place_theme'), ('suitableFor', 'place_companion')]:
        expected = sum(a['predicate'] == predicate for a in data['annotations'])
        assert db.execute('SELECT COUNT(*) FROM ' + table + " WHERE review_status='DRAFT'").fetchone()[0] == expected
    assert db.execute("SELECT active FROM place WHERE code='PLACE_A'").fetchone()[0] == (not replace)
    assert db.execute("SELECT active FROM region WHERE code='REGION_EMPTY'").fetchone()[0] == (not replace)
    assert db.execute('SELECT revision FROM ontology_revision WHERE id=1').fetchone()[0] == 2
    # Import a selected addition into an existing dataset without changing reviewed rows.
    selected = data['places'][-2:]
    for place in selected:
        place_id = db.execute('SELECT id FROM place WHERE code=?', (place['code'],)).fetchone()[0]
        for table in ('place_source', 'place_theme', 'place_companion'):
            db.execute('DELETE FROM ' + table + ' WHERE place_id=?', (place_id,))
        db.execute('DELETE FROM place WHERE id=?', (place_id,))
    db.execute("UPDATE place SET review_status='APPROVED', evidence_note='관리자 검토 내용' WHERE code=?", (data['places'][0]['code'],))
    db.commit()
    selected_command = command + [arg for p in selected for arg in ('--place-code', p['code'])]
    selected_sql = subprocess.check_output(selected_command, text=True)
    db.executescript(selected_sql.replace("SET TIME ZONE 'UTC'; ", ''))
    assert db.execute("SELECT review_status,evidence_note FROM place WHERE code=?", (data['places'][0]['code'],)).fetchone() == ('APPROVED', '관리자 검토 내용')
    assert db.execute("SELECT COUNT(*) FROM place WHERE material_kind='REAL'").fetchone()[0] == len(data['places'])
    assert db.execute('SELECT COUNT(*) FROM place_source').fetchone()[0] == sum(len(p['source_ids']) - 1 for p in data['places'])
    try:
        db.executescript(selected_sql.replace("SET TIME ZONE 'UTC'; ", ''))
        raise AssertionError('Duplicate imports must fail')
    except sqlite3.IntegrityError:
        db.rollback()
    invalid = subprocess.run(command + ['--place-code', 'PLACE_MISSING'], capture_output=True, text=True)
    assert invalid.returncode != 0 and not invalid.stdout
    db.close()

print('PASS: tourism provenance, DRAFT relations, addresses, selected imports, review preservation, duplicate rejection and revision')
