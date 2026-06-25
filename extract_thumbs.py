import cv2

videos = [
    "onboarding", "low-vision", "font-size",
    "route-search", "bookmark", "report"
]

for name in videos:
    cap = cv2.VideoCapture(f"Docs/media/{name}.mp4")
    ret, frame = cap.read()
    if ret:
        cv2.imwrite(f"Docs/media/{name}_thumb.png", frame)
        print(f"완료: {name}_thumb.png")
    else:
        print(f"실패: {name}.mp4 파일 확인 필요")
    cap.release()
