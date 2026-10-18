"""
Datos ficticios para el backend de demo (scripts/demo.sh): una familia con 4 personas, 2 calendarios, eventos alrededor
de hoy, rutinas de lunes a viernes y 2 listas de tareas. Las fechas son relativas al día en que se ejecuta.

Uso: python3 scripts/demo/seed.py [url-del-backend]   (por defecto http://127.0.0.1:3001)
"""
import json, sys, urllib.request, datetime
T = datetime.date.today()
def d(n, hm): return f"{T + datetime.timedelta(days=n)} {hm}:00"
B = sys.argv[1].rstrip("/") if len(sys.argv) > 1 else "http://127.0.0.1:3001"
tok = None
def call(method, path, data=None):
    req = urllib.request.Request(B + path, data=json.dumps(data or {}).encode(), method=method,
                                 headers={"Content-Type": "application/json", **({"Authorization": "Bearer " + tok} if tok else {})})
    with urllib.request.urlopen(req) as r: return json.load(r)
u = call("POST", "/user/create", {"name": "Familia Demo", "email": "demo@planify.example", "password": "demo1234"})
tok = u["token"]
P = {n: call("POST", "/person/create", {"name": n, "color": c})["id"] for n, c in
     [("Ana", "#2563EB"), ("Marcos", "#16A34A"), ("Lucía", "#9333EA"), ("Pablo", "#EA580C")]}
fam = call("POST", "/calendar/create", {"name": "Familia", "color": "#6923E2"})["id"]
col = call("POST", "/calendar/create", {"name": "Colegio", "color": "#0EA5E9"})["id"]
for cal, title, at, who in [
    (fam, "Revisión médica", d(-12, "10:00"), ["Marcos"]),
    (col, "Tutoría", d(-8, "17:00"), ["Ana"]),
    (fam, "Cumpleaños del abuelo", d(-5, "13:30"), []),
    (fam, "Mercadillo", d(-2, "11:00"), ["Lucía", "Pablo"]),
    (fam, "Desayuno con la vecina", d(0, "09:00"), ["Ana"]),
    (fam, "Cena con los abuelos", d(0, "20:30"), ["Ana", "Marcos"]),
    (fam, "Dentista", d(1, "17:00"), ["Lucía"]),
    (fam, "Partido de fútbol", d(2, "10:30"), ["Pablo"]),
    (fam, "Comida familiar", d(3, "14:00"), []),
    (fam, "Revisión del coche", d(5, "09:00"), ["Marcos"]),
    (col, "Reunión de padres", d(6, "18:00"), ["Ana"]),
    (fam, "Cumpleaños de Lucía", d(7, "17:30"), ["Lucía"]),
    (col, "Excursión al museo", d(8, "09:30"), ["Pablo"]),
    (fam, "Cine en familia", d(9, "19:00"), []),
]:
    call("POST", "/event/create", {"calendar_id": cal, "title": title, "date_at": at, "person_ids": [P[w] for w in who]})
for title, days, time, who in [
    ("Recoger a los niños", [1, 2, 3, 4, 5], "16:00", ["Ana"]),
    ("Piano", [4], "16:45", ["Lucía"]),
    ("Natación", [2, 4], "18:00", ["Pablo"]),
    ("Inglés", [1, 3], "17:30", ["Lucía"]),
    ("Fútbol", [5], "17:00", ["Pablo"]),
    ("Sacar al perro", [1, 2, 3, 4, 5, 6, 7], "19:30", ["Marcos"]),
    ("Paseo en bici", [6, 7], "11:00", ["Pablo"]),
]:
    call("POST", "/routine/create?timezone=Europe/Madrid", {"calendar_id": col, "title": title, "days": days, "time": time,
         "start_date": str(T - datetime.timedelta(days=30)), "active": True, "person_ids": [P[w] for w in who]})
for name, color, tasks in [("Compra", "#F59E0B", ["Leche", "Pan", "Huevos", "Fruta", "Café"]),
                           ("Casa", "#10B981", ["Cambiar la bombilla del pasillo", "Llamar al fontanero", "Regar las plantas"])]:
    lid = call("POST", "/task-list/create", {"name": name, "color": color})["id"]
    for t in tasks: call("POST", "/task/create", {"task_list_id": lid, "title": t})
print("ok", len(call("GET", "/event?dashboard=true&routine=false&timezone=Europe/Madrid")), "eventos")
