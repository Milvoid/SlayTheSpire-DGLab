import websocket
import json
import qrcode
import threading
from flask import Flask, request

SERVER_URL = "wss://ws.dungeon-lab.cn"
APP_DOWNLOAD = "https://www.dungeon-lab.com/app-download.php"
QR_LABEL = "DGLAB-SOCKET"

# 接收事件并发送波形
def start_flask(ws, client_id, target_id):
    app = Flask(__name__)

    @app.route("/event", methods=["POST"])
    def receive_event():
        data = request.get_json(force=True)

        if data.get("event") == "LightningOrb":
            damage_base = data.get("damage_base")
            damage_output = data.get("damage_output")
            hit_all = data.get("hit_all")
            focus = data.get("focus")
            enemy_num = data.get("enemy_num")

            send_simple_wave(ws, client_id, target_id, "A", 1, min(damage_output/10, 1))
            send_simple_wave(ws, client_id, target_id, "B", 1, min(damage_output/10, 1))

            print(f"[LightningOrb] {data}")
        else:
            print(f"收到未知事件: {data}")

        return "ok", 200

    # 启动 Flask
    app.run(host="0.0.0.0", port=63333)

# 生成绑定二维码
def generate_qr(client_id):
    url = f"{APP_DOWNLOAD}#{QR_LABEL}#wss://ws.dungeon-lab.cn/{client_id}"
    print("[QR] 绑定二维码内容：", url)
    img = qrcode.make(url)
    img.show()

# 绑定 App
def wait_for_bind(ws):
    print("[INFO] 等待 APP 绑定中...")
    while True:
        raw = ws.recv()
        data = json.loads(raw)
        # 绑定成功
        if data.get("type") == "bind" and data.get("message") == "200":
            print("[SUCCESS] 绑定成功！")
            return data["targetId"]
        # 初次分配 clientId
        elif data.get("type") == "bind" and data.get("message") == "DGLAB":
            print("[INFO] 收到来自 APP 的绑定请求（clientId 已分配）")
        else:
            print("[DEBUG]", data)

# 接受 ws 服务端信息
def receive_loop(ws):
    """后台线程：不断接收并打印所有后续消息"""
    while True:
        try:
            raw = ws.recv()
            data = json.loads(raw)
            print(f"[RECV] {data}")
        except Exception as e:
            print(f"[RECV ERROR] {e}")
            break

# 发送强度数据
def send_strength(ws, client_id, target_id, t, strength, channel):
    """
    发送前端协议格式的强度控制指令：
      - t: 1 减少；2 增加；3 归零；4 设定
      - strength: 数值
      - channel: 1=A；2=B
    """
    packet = {
        "type": int(t),
        "strength": int(strength),
        "message": "set channel",
        "channel": int(channel),
        "clientId": client_id,
        "targetId": target_id
    }
    print(f"[SEND] {packet}")
    ws.send(json.dumps(packet))

# 示例简单波形
def send_simple_wave(ws, client_id, target_id, channel="A", time=2, multi=1.0):
    """
    向指定通道发送一段简单的波形数据。

    参数：
    - ws: WebSocket 连接对象
    - client_id: 本控制端 ID
    - target_id: 目标 APP ID
    - channel: "A" 或 "B"
    - time: 持续时间 (单位 100ms)
    - multi: 强度倍数
    """
    # 原始波形模板（强度逐步上升）
    base_wave = [
        "00000000", "14141414",
        "28282828", "3C3C3C3C", 
        "50505050", "64646464"
    ]

    # 拼接前缀 + 强度调整（后 4 字节）
    wave_hex = []
    for val in base_wave:
        prefix = "0A0A0A0A"
        suffix = val
        if multi != 1.0:
            num = int(val[:2], 16) 
            num = max(0, min(int(num * multi), 255))
            hexval = f"{num:02X}" * 4
            suffix = hexval
        wave_hex.append(prefix + suffix)

    # 构造 JSON payload
    formatted_wave = ",".join(['"{}"'.format(s) for s in wave_hex])
    packet = {
        "type": "clientMsg",
        "message": f"{channel}:[{formatted_wave}]",
        "time": time,
        "channel": channel,
        "clientId": client_id,
        "targetId": target_id
    }

    print(f"[SEND WAVE] channel={channel}, time={time}, multi={multi}")
    ws.send(json.dumps(packet))

if __name__ == "__main__":
    print("[INFO] 连接服务器中:", SERVER_URL)
    ws = websocket.WebSocket()
    ws.connect(SERVER_URL)

    # 接收并打印初次分配的 clientId
    initial = json.loads(ws.recv())
    client_id = initial["clientId"]
    print("[INFO] 控制端 ID:", client_id)

    # 生成二维码供 APP 扫码绑定
    generate_qr(client_id)

    # 等待绑定成功，并拿到 target_id
    target_id = wait_for_bind(ws)

    # 启动后台接收线程，打印所有后续消息
    recv_thread = threading.Thread(target=receive_loop, args=(ws,), daemon=True)
    recv_thread.start()

    start_flask(ws, client_id, target_id)

    print("[INFO] 断开连接")
    ws.close()

    