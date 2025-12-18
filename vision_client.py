
import requests
import io

class VisionClient:
    def __init__(self, screenshot_base_url: str, vision_explain_url: str, auth_token: str):
        self.screenshot_base_url = screenshot_base_url
        self.vision_explain_url = vision_explain_url
        self.auth_token = auth_token

    def get_screenshot(self) -> bytes:
        """
        从指定的URL获取屏幕截图。
        """
        screenshot_url = f"{self.screenshot_base_url}/screenshot"
        headers = {
            "Authorization": f"Bearer {self.auth_token}"
        }
        try:
            response = requests.get(screenshot_url, headers=headers, timeout=15)
            response.raise_for_status()  # 如果请求失败，抛出HTTPError
            return response.content
        except requests.exceptions.RequestException as e:
            print(f"获取屏幕截图失败: {e}")
            return None

    def upload_image_for_explanation(self, image_data: bytes, question: str) -> dict:
        """
        将图片和问题上传到视觉识别服务进行解释。
        """
        if not image_data:
            print("没有图片数据可上传。")
            return None

        headers = {
            "Authorization": f"Bearer {self.auth_token}"
        }
        
        # 使用 multipart/form-data 格式
        files = {
            "file": ("screenshot.jpg", io.BytesIO(image_data), "image/jpeg"),
        }
        data = {
            "question": question
        }

        try:
            response = requests.post(self.vision_explain_url, headers=headers, files=files, data=data, timeout=30)
            response.raise_for_status()  # 如果请求失败，抛出HTTPError
            return response.json()
        except requests.exceptions.RequestException as e:
            print(f"上传图片进行解释失败: {e}")
            return None

if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Android屏幕截图并上传AI识图工具")
    parser.add_argument("--screenshot_base_url", type=str, default="http://192.168.6.134:8080",
                        help="Android设备HTTP API的基础URL，例如 http://192.168.6.134:8080")
    parser.add_argument("--vision_explain_url", type=str, default="http://api.xiaozhi.me/vision/explain",
                        help="AI识图服务的URL，例如 http://api.xiaozhi.me/vision/explain")
    parser.add_argument("--token", type=str, default="test-token",
                        help="认证Token，用于HTTP请求的Authorization头部 (Bearer Token)")
    parser.add_argument("--question", type=str, default="请描述屏幕上的内容",
                        help="向AI提出的问题，描述你希望了解的屏幕内容")

    args = parser.parse_args()

    client = VisionClient(args.screenshot_base_url, args.vision_explain_url, args.token)

    print(f"正在从 {args.screenshot_base_url}/screenshot 获取屏幕截图...")
    image_data = client.get_screenshot()

    if image_data:
        print(f"成功获取到 {len(image_data)} 字节的屏幕截图。")
        print(f"正在上传图片到 {args.vision_explain_url} 进行识别，问题: '{args.question}'...")
        explanation = client.upload_image_for_explanation(image_data, args.question)

        if explanation:
            print("识别结果:")
            print(explanation)
        else:
            print("未能获取识别结果。")
    else:
        print("未能获取屏幕截图，无法进行识别。")