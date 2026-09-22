"""Race cancellation/deactivation with redemption after the base API checks."""
from api_check import Client
from concurrent.futures import ThreadPoolExecutor
from threading import Barrier
v, staff, admin = Client('visitor2'), Client('staff-a'), Client('admin')
for target in ('cancellation','merchant','benefit'):
    p=v.request('POST','/me/passes',{'productId':'1'},expected=201)[1]
    lookup=staff.request('POST','/merchant/pass-lookups',{'merchantId':'1','code':p['code']})[1]
    issued_id=lookup['benefits'][0]['issuedBenefitId']
    barrier=Barrier(2)
    def use():
        barrier.wait()
        try:
            staff.request('POST','/merchant/redemptions',{'confirmationToken':lookup['confirmationToken'],'issuedBenefitId':issued_id},expected=201)
            return True
        except AssertionError as e:
            expected={'cancellation':'PASS_CANCELLED','merchant':'MERCHANT_INACTIVE','benefit':'BENEFIT_INACTIVE'}[target]
            assert e.args[0][2]==409 and e.args[0][3]['error']['code']==expected,e
            return False
    def block():
        barrier.wait()
        if target=='cancellation': admin.request('PUT','/admin/passes/'+p['id']+'/cancellation',{'reason':'경쟁 검증'})
        else: admin.request('PATCH','/admin/'+('merchants' if target=='merchant' else 'benefits')+'/1',{'active':False})
    with ThreadPoolExecutor(2) as pool:
        usage=pool.submit(use); change=pool.submit(block)
        success=usage.result();change.result()
    detail=v.request('GET','/me/passes/'+p['id'])[1]
    assert detail['benefits'][0]['remainingCount']==(0 if success else 1)
    assert len(v.request('GET','/me/redemptions?passId='+p['id'])[1]['items'])==int(success)
    if target!='cancellation':
        admin.request('PATCH','/admin/'+('merchants' if target=='merchant' else 'benefits')+'/1',{'active':True})
        admin.request('PUT','/admin/passes/'+p['id']+'/cancellation',{'reason':'검증 종료'})
print('PASS: redemption racing cancellation, merchant deactivation and benefit deactivation stays atomic')
