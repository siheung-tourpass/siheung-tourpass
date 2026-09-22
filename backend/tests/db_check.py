"""Read-only persisted invariants and SQL CHECK enforcement on a test DB.
Usage: python3 backend/tests/db_check.py 127.0.0.1 [database]
"""
import json
import subprocess
import sys
from pathlib import Path

data = json.loads((Path(__file__).resolve().parents[1] / 'data/siheung-tourism.json').read_text())
connection = sys.argv[1]
database = sys.argv[2] if len(sys.argv)>2 else "siheung_tourpass"
def sql(statement, ok=True):
    result = subprocess.run(['psql','-X','-v','ON_ERROR_STOP=1','-h',connection,'-At','-d',database,'-c',statement],capture_output=True,text=True)
    assert (result.returncode==0)==ok, result.stderr
    return result.stdout.strip()
assert sql("SELECT COUNT(*) FROM pg_constraint WHERE conname IN ('journey_place_journey_id_fkey','journey_pass_history_journey_id_fkey') AND confdeltype='c'")=='2'
assert sql('SELECT COUNT(*) FROM issued_pass')=='1' 
assert sql('SELECT COUNT(*) FROM redemption')=='1'
assert sql('SELECT COUNT(*) FROM issued_benefit WHERE remaining_count=0')=='1'
assert sql('SELECT COUNT(*) FROM issued_benefit ib LEFT JOIN redemption r ON r.issued_benefit_id=ib.id WHERE (ib.remaining_count=0 AND r.id IS NULL) OR (ib.remaining_count=1 AND r.id IS NOT NULL)')=='0'
assert sql("SELECT COUNT(*) FROM place WHERE material_kind='REAL' AND review_status='DRAFT'")==str(len(data['places']))
assert sql('SELECT COUNT(*) FROM feedback')=='1'
assert sql('SELECT COUNT(*) FROM feedback_theme')=='2'
assert sql('SELECT COUNT(*) FROM feedback_reason')=='1'
sql('START TRANSACTION; UPDATE benefit SET payable_won=-1 WHERE id=1; ROLLBACK;',ok=False)
sql('START TRANSACTION; UPDATE feedback SET rating=0; ROLLBACK;',ok=False)
sql('START TRANSACTION; UPDATE issued_pass SET expires_at=starts_at; ROLLBACK;',ok=False)
sql('START TRANSACTION; UPDATE issued_benefit SET remaining_count=2 WHERE id=1; ROLLBACK;',ok=False)
sql("START TRANSACTION; UPDATE place SET source_url='https://example.com' WHERE id=1; ROLLBACK;",ok=False)
print('PASS: persisted counts, atomic redemption, DRAFT import, feedback children and CHECK constraints')

# A token collision must leave the transaction usable for another INSERT.
assert sql("BEGIN; INSERT INTO issued_pass(owner_id,product_id,code,product_name,starts_at,expires_at) SELECT owner_id,product_id,code,product_name,starts_at,expires_at FROM issued_pass LIMIT 1 ON CONFLICT(code) DO NOTHING; SELECT 'collision-safe'; ROLLBACK;").splitlines()[-2] == 'collision-safe'
print('PASS: PostgreSQL code collision preserves the transaction')
