"""Authoritative cinema and showtime rules for the public browsing API."""
from __future__ import annotations
import math
import sqlite3
from datetime import date, datetime, time, timedelta, timezone
from typing import Any
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

VALID_SORTS = {"recommended", "distance", "price"}

class QueryValidationError(ValueError):
    def __init__(self, code: str, message: str, field: str | None = None):
        super().__init__(message); self.code, self.message, self.field = code, message, field

def utc_now() -> datetime: return datetime.now(timezone.utc)
def iso_utc(value: datetime) -> str: return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")
def parse_instant(value: str) -> datetime: return datetime.fromisoformat(value.replace("Z", "+00:00")).astimezone(timezone.utc)
def parse_zone(value: str) -> ZoneInfo:
    try: return ZoneInfo(value)
    except ZoneInfoNotFoundError as error: raise QueryValidationError("INVALID_TIME_ZONE", "影院时区无效", "timeZone") from error
def parse_local_date(value: str) -> date:
    try: return date.fromisoformat(value)
    except (TypeError, ValueError) as error: raise QueryValidationError("INVALID_DATE", "date 必须是 ISO 日期", "date") from error
def parse_local_time(value: str | None, field: str) -> time | None:
    if value is None: return None
    try: return time.fromisoformat(value)
    except ValueError as error: raise QueryValidationError("INVALID_TIME", f"{field} 必须是 HH:mm", field) from error

def initialize_showtime_schema(connection: sqlite3.Connection) -> None:
    connection.executescript("""
    CREATE TABLE IF NOT EXISTS cinemas(id TEXT PRIMARY KEY NOT NULL,name TEXT NOT NULL,address TEXT NOT NULL,
      city_code TEXT NOT NULL,district TEXT NOT NULL,latitude REAL,longitude REAL,time_zone TEXT NOT NULL,
      status TEXT NOT NULL CHECK(status IN ('OPEN','TEMPORARILY_CLOSED','CLOSED')),
      recommendation_rank INTEGER NOT NULL CHECK(recommendation_rank>=0),
      CHECK((latitude IS NULL AND longitude IS NULL) OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)));
    CREATE INDEX IF NOT EXISTS idx_cinemas_city ON cinemas(city_code);
    CREATE TABLE IF NOT EXISTS auditoriums(id TEXT PRIMARY KEY NOT NULL,
      cinema_id TEXT NOT NULL REFERENCES cinemas(id) ON DELETE RESTRICT,name TEXT NOT NULL,seat_layout_ref TEXT,
      status TEXT NOT NULL CHECK(status IN ('ACTIVE','INACTIVE')),UNIQUE(cinema_id,name));
    CREATE TABLE IF NOT EXISTS showtimes(id TEXT PRIMARY KEY NOT NULL,movie_id INTEGER NOT NULL CHECK(movie_id>0),
      cinema_id TEXT NOT NULL REFERENCES cinemas(id) ON DELETE RESTRICT,
      auditorium_id TEXT NOT NULL REFERENCES auditoriums(id) ON DELETE RESTRICT,
      starts_at TEXT NOT NULL,ends_at TEXT NOT NULL,language TEXT NOT NULL,format TEXT NOT NULL,
      base_price_minor INTEGER NOT NULL CHECK(base_price_minor>=0),currency TEXT NOT NULL CHECK(length(currency)=3),
      sales_status TEXT NOT NULL CHECK(sales_status IN ('ON_SALE','STOPPED','SOLD_OUT','CANCELLED')),
      version INTEGER NOT NULL CHECK(version>=1),updated_at TEXT NOT NULL,CHECK(ends_at>starts_at),
      UNIQUE(auditorium_id,starts_at));
    CREATE INDEX IF NOT EXISTS idx_showtimes_movie_starts ON showtimes(movie_id,starts_at);
    CREATE INDEX IF NOT EXISTS idx_showtimes_cinema_starts ON showtimes(cinema_id,starts_at);
    CREATE TABLE IF NOT EXISTS showtime_versions(showtime_id TEXT NOT NULL,version INTEGER NOT NULL,
      starts_at TEXT NOT NULL,ends_at TEXT NOT NULL,auditorium_id TEXT NOT NULL,base_price_minor INTEGER NOT NULL,
      sales_status TEXT NOT NULL,PRIMARY KEY(showtime_id,version));
    """)

def seed_development_data(connection: sqlite3.Connection, anchor: datetime | None = None) -> None:
    current = (anchor or utc_now()).astimezone(timezone.utc)
    local_day = current.astimezone(ZoneInfo("Asia/Shanghai")).date()
    cinemas = [
      ("cin_sh_001","浦东星光影城","浦东新区世纪大道100号","CN-SH","浦东新区",31.235,121.505,"Asia/Shanghai","OPEN",1),
      ("cin_sh_002","黄浦国际影城","黄浦区南京东路200号","CN-SH","黄浦区",31.232,121.475,"Asia/Shanghai","OPEN",2),
      ("cin_sh_003","徐汇艺术影城","徐汇区衡山路88号","CN-SH","徐汇区",31.205,121.445,"Asia/Shanghai","OPEN",3)]
    connection.executemany("INSERT INTO cinemas VALUES (?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET "
      "name=excluded.name,address=excluded.address,city_code=excluded.city_code,district=excluded.district,"
      "latitude=excluded.latitude,longitude=excluded.longitude,time_zone=excluded.time_zone,status=excluded.status,"
      "recommendation_rank=excluded.recommendation_rank", cinemas)
    extra_cinemas = [
      ("cin_sh_004","Metro Cinema Pudong","Pudong Century Avenue 188","CN-SH","Pudong",31.228,121.515,"Asia/Shanghai","OPEN",4),
      ("cin_sh_005","Metro Cinema Xuhui","Xuhui Caoxi Road 268","CN-SH","Xuhui",31.190,121.437,"Asia/Shanghai","OPEN",5),
      ("cin_sh_006","Metro Cinema Jingan","Jingan West Nanjing Road 999","CN-SH","Jingan",31.228,121.445,"Asia/Shanghai","OPEN",6),
      ("cin_sh_007","Metro Cinema Hongqiao","Changning Tianshan Road 777","CN-SH","Changning",31.207,121.405,"Asia/Shanghai","OPEN",7)]
    connection.executemany("INSERT INTO cinemas VALUES (?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET "
      "name=excluded.name,address=excluded.address,city_code=excluded.city_code,district=excluded.district,"
      "latitude=excluded.latitude,longitude=excluded.longitude,time_zone=excluded.time_zone,status=excluded.status,"
      "recommendation_rank=excluded.recommendation_rank", extra_cinemas)
    auditoriums = [("aud_sh_001","cin_sh_001","1号激光厅","layout-100","ACTIVE"),
      ("aud_sh_002","cin_sh_001","IMAX厅","layout-imax","ACTIVE"),
      ("aud_sh_003","cin_sh_002","杜比厅","layout-dolby","ACTIVE"),
      ("aud_sh_004","cin_sh_003","艺术厅","layout-art","ACTIVE")]
    connection.executemany("INSERT INTO auditoriums VALUES (?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET "
      "cinema_id=excluded.cinema_id,name=excluded.name,seat_layout_ref=excluded.seat_layout_ref,status=excluded.status", auditoriums)
    extra_auditoriums = [(f"aud_sh_{index:03d}", f"cin_sh_{index:03d}", "Hall 1", "layout-100", "ACTIVE") for index in range(5, 8)]
    connection.executemany("INSERT INTO auditoriums VALUES (?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET "
      "cinema_id=excluded.cinema_id,name=excluded.name,seat_layout_ref=excluded.seat_layout_ref,status=excluded.status", extra_auditoriums)
    def instant(offset: int, hour: int, minute: int=0) -> datetime:
        return datetime.combine(local_day+timedelta(days=offset),time(hour,minute),ZoneInfo("Asia/Shanghai")).astimezone(timezone.utc)
    seed = [("st_sh_001",1,"cin_sh_001","aud_sh_001",instant(0,18,30),instant(0,20,40),"zh-CN","2D",4500,"CNY","ON_SALE",1),
      ("st_sh_002",1,"cin_sh_001","aud_sh_002",instant(0,20,45),instant(0,23,15),"en-US","IMAX",5500,"CNY","ON_SALE",1),
      ("st_sh_003",1,"cin_sh_002","aud_sh_003",instant(0,14),instant(0,16,10),"zh-CN","杜比",6800,"CNY","ON_SALE",1),
      ("st_sh_004",1,"cin_sh_003","aud_sh_004",instant(1,22,30),instant(2,0,40),"en-US","2D",3900,"CNY","ON_SALE",1),
      ("st_sh_started",1,"cin_sh_002","aud_sh_003",instant(-1,9),instant(-1,11),"zh-CN","2D",4000,"CNY","ON_SALE",1)]
    for row in seed:
        values=(*row[:4],iso_utc(row[4]),iso_utc(row[5]),*row[6:],iso_utc(current))
        # Never overwrite operator changes (price/status/version) on a later process start.
        connection.execute("INSERT OR IGNORE INTO showtimes VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",values)
        connection.execute("INSERT OR IGNORE INTO showtime_versions VALUES (?,?,?,?,?,?,?)",
          (row[0],row[11],iso_utc(row[4]),iso_utc(row[5]),row[3],row[8],row[10]))
    # Broad deterministic fixtures keep the complete purchase flow usable
    # before a real cinema provider is connected: seven cinemas, ten movie IDs,
    # three showtimes per movie and two future dates. Re-running this function
    # is safe because all IDs are fixed and writes are idempotent.
    demo_slots = ((0, 11, 30, 4500), (0, 15, 0, 5200), (1, 19, 30, 5800))
    for cinema_index in range(1, 8):
        cinema_id = f"cin_sh_{cinema_index:03d}"
        auditorium_id = f"aud_sh_{cinema_index:03d}" if cinema_index <= 4 else f"aud_sh_{cinema_index:03d}"
        for movie_id in range(1, 11):
            for slot_index, (day_offset, hour, minute, price) in enumerate(demo_slots, start=1):
                # Stagger each movie onto its own deterministic local day so
                # auditorium/time uniqueness is preserved while every movie
                # still receives three daily showtime choices.
                starts = instant(day_offset + movie_id - 1, hour, minute)
                ends = starts + timedelta(minutes=130)
                showtime_id = f"demo_sh_{movie_id:02d}_{cinema_index:02d}_{slot_index:02d}"
                values = (showtime_id, movie_id, cinema_id, auditorium_id, iso_utc(starts), iso_utc(ends),
                          "zh-CN" if slot_index != 3 else "en-US", "2D" if slot_index != 2 else "IMAX",
                          price, "CNY", "ON_SALE", 1, iso_utc(current))
                connection.execute("INSERT OR IGNORE INTO showtimes VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", values)
                connection.execute("INSERT OR IGNORE INTO showtime_versions VALUES (?,?,?,?,?,?,?)",
                                   (showtime_id, 1, iso_utc(starts), iso_utc(ends), auditorium_id, price, "ON_SALE"))

def unavailability(row: sqlite3.Row, now: datetime) -> str | None:
    if row["cinema_status"]!="OPEN": return "CINEMA_CLOSED"
    if row["auditorium_status"]!="ACTIVE": return "AUDITORIUM_INACTIVE"
    if row["sales_status"]=="CANCELLED": return "CANCELLED"
    if row["sales_status"]=="STOPPED": return "SALES_STOPPED"
    if row["sales_status"]=="SOLD_OUT": return "SOLD_OUT"
    if parse_instant(row["ends_at"])<=now: return "ENDED"
    if parse_instant(row["starts_at"])<=now: return "STARTED"
    return None

def joined_showtimes(connection: sqlite3.Connection,movie_id:int|None=None,showtime_id:str|None=None)->list[sqlite3.Row]:
    where,params=("s.movie_id=?",(movie_id,)) if movie_id is not None else ("s.id=?",(showtime_id,))
    return connection.execute("SELECT s.*,c.name cinema_name,c.address,c.city_code,c.district,c.latitude,c.longitude,"
      "c.time_zone,c.status cinema_status,c.recommendation_rank,a.name auditorium_name,a.status auditorium_status "
      "FROM showtimes s JOIN cinemas c ON c.id=s.cinema_id JOIN auditoriums a ON a.id=s.auditorium_id WHERE "+where,params).fetchall()

def available_dates(connection:sqlite3.Connection,movie_id:int,city_code:str,now:datetime)->list[str]:
    values=set()
    for row in joined_showtimes(connection,movie_id=movie_id):
        if row["city_code"]==city_code and unavailability(row,now) is None:
            values.add(parse_instant(row["starts_at"]).astimezone(parse_zone(row["time_zone"])).date().isoformat())
    return sorted(values)

def showtime_json(row:sqlite3.Row,now:datetime)->dict[str,Any]:
    reason=unavailability(row,now)
    return {"id":row["id"],"movieId":row["movie_id"],"cinemaId":row["cinema_id"],
      "auditoriumId":row["auditorium_id"],"auditoriumName":row["auditorium_name"],"startsAt":row["starts_at"],
      "endsAt":row["ends_at"],"timeZone":row["time_zone"],"language":row["language"],"format":row["format"],
      "basePrice":{"amountMinor":row["base_price_minor"],"currency":row["currency"]},
      "salesStatus":row["sales_status"],"selectable":reason is None,"unavailableReason":reason,"version":row["version"]}

def haversine_meters(lat1:float,lon1:float,lat2:float,lon2:float)->int:
    radius=6_371_000.; p1,p2=math.radians(lat1),math.radians(lat2); dp=math.radians(lat2-lat1); dl=math.radians(lon2-lon1)
    a=math.sin(dp/2)**2+math.cos(p1)*math.cos(p2)*math.sin(dl/2)**2
    return round(radius*2*math.atan2(math.sqrt(a),math.sqrt(1-a)))

def query_cinemas(connection:sqlite3.Connection,movie_id:int,city_code:str,local_date:date,now:datetime,
  districts:set[str],start_local:time|None,end_local:time|None,min_price:int|None,max_price:int|None,
  requested_sort:str,latitude:float|None,longitude:float|None)->dict[str,Any]:
    all_rows=[r for r in joined_showtimes(connection,movie_id=movie_id) if r["city_code"]==city_code]
    available_districts=sorted({r["district"] for r in all_rows}); grouped={}
    for row in all_rows:
        local_start=parse_instant(row["starts_at"]).astimezone(parse_zone(row["time_zone"])); price=int(row["base_price_minor"])
        if local_start.date()!=local_date or unavailability(row,now) is not None: continue
        if districts and row["district"] not in districts: continue
        local_clock=local_start.time().replace(tzinfo=None)
        if start_local and local_clock<start_local: continue
        if end_local and local_clock>end_local: continue
        if min_price is not None and price<min_price: continue
        if max_price is not None and price>max_price: continue
        grouped.setdefault(row["cinema_id"],[]).append(row)
    applied,notice=requested_sort,None
    if requested_sort=="distance" and (latitude is None or longitude is None or any(rs[0]["latitude"] is None or rs[0]["longitude"] is None for rs in grouped.values())):
        applied,notice="recommended","LOCATION_UNAVAILABLE"
    cinemas=[]
    for cid,rows in grouped.items():
        first=rows[0]; distance=None if applied!="distance" else haversine_meters(latitude,longitude,first["latitude"],first["longitude"])
        minimum=min(int(r["base_price_minor"]) for r in rows)
        cinemas.append({"id":cid,"name":first["cinema_name"],"address":first["address"],"cityCode":first["city_code"],
          "district":first["district"],"timeZone":first["time_zone"],"distanceMeters":distance,
          "minimumPrice":{"amountMinor":minimum,"currency":rows[0]["currency"]},
          "showtimes":[showtime_json(r,now) for r in sorted(rows,key=lambda x:x["starts_at"])],"_rank":first["recommendation_rank"]})
    if applied=="price": cinemas.sort(key=lambda x:(x["minimumPrice"]["amountMinor"],x["_rank"],x["id"]))
    elif applied=="distance": cinemas.sort(key=lambda x:(x["distanceMeters"],x["id"]))
    else: cinemas.sort(key=lambda x:(x["_rank"],x["id"]))
    for cinema in cinemas: cinema.pop("_rank",None)
    return {"requestedSort":requested_sort,"appliedSort":applied,"sortNotice":notice,"availableDistricts":available_districts,"cinemas":cinemas}

def validate_showtime(connection:sqlite3.Connection,showtime_id:str,observed_version:int,now:datetime)->dict[str,Any]|None:
    rows=joined_showtimes(connection,showtime_id=showtime_id)
    if not rows:return None
    row=rows[0]; latest=showtime_json(row,now); changed=[]
    old=connection.execute("SELECT * FROM showtime_versions WHERE showtime_id=? AND version=?",(showtime_id,observed_version)).fetchone()
    if old is None and observed_version!=row["version"]: changed=["PRICE","START_TIME","END_TIME","AUDITORIUM","STATUS"]
    elif old is not None:
        if old["base_price_minor"]!=row["base_price_minor"]:changed.append("PRICE")
        if old["starts_at"]!=row["starts_at"]:changed.append("START_TIME")
        if old["ends_at"]!=row["ends_at"]:changed.append("END_TIME")
        if old["auditorium_id"]!=row["auditorium_id"]:changed.append("AUDITORIUM")
        if old["sales_status"]!=row["sales_status"]:changed.append("STATUS")
    if latest["unavailableReason"]:result,message="UNAVAILABLE","SHOWTIME_UNAVAILABLE"
    elif observed_version!=row["version"] or changed:result,message="CHANGED","SHOWTIME_CHANGED"
    else:result,message="UNCHANGED","SHOWTIME_CURRENT"
    return {"result":result,"serverTime":iso_utc(now),"changedFields":changed,"messageCode":message,"latest":latest}

def set_showtime_sales_status(connection: sqlite3.Connection, showtime_id: str, status: str, now: datetime) -> bool:
    """Operator workflow used by stop-sale/cancellation paths.

    Seat locks are invalidated in the same database transaction so a stopped
    showtime cannot leave a customer with a payment-capable lock.
    """
    if status not in {'ON_SALE', 'STOPPED', 'SOLD_OUT', 'CANCELLED'}:
        raise QueryValidationError('INVALID_SALES_STATUS', 'Unsupported sales status', 'salesStatus')
    changed = connection.execute(
        "UPDATE showtimes SET sales_status=?, version=version+1, updated_at=? WHERE id=?",
        (status, iso_utc(now), showtime_id),
    ).rowcount
    if changed and status in {'STOPPED', 'CANCELLED'}:
        from seats import invalidate_showtime_locks
        invalidate_showtime_locks(connection, showtime_id, now)
    return changed == 1
