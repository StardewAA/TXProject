import app as m
import integration
from fastapi import FastAPI, Header

def actor(authorization: str=Header('')):
    if authorization=='Bearer test-admin':return m.Actor(id=1,name='验收管理员',role='admin',tenant_id=90001,super_admin=True)
    if authorization=='Bearer test-learner':return m.Actor(id=101,name='验收学员',role='learner',tenant_id=90001)
    raise m.HTTPException(401,'Test identity required')
m.app.dependency_overrides[m.current_user]=actor
integration.platform_get=lambda *a:[{'id':1,'nickname':'验收管理员'},{'id':101,'nickname':'验收学员'}]
app=FastAPI()
app.mount('/training-exam',m.app)
