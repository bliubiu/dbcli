import requests
import json
import time

# 测试报告生成功能
def test_report_generation():
    url = "http://localhost:8080/api/generate-report"
    
    # 测试Excel报告生成
    print("测试Excel报告生成...")
    payload = {"format": "excel"}
    headers = {"Content-Type": "application/json"}
    
    try:
        response = requests.post(url, data=json.dumps(payload), headers=headers)
        print(f"响应状态码: {response.status_code}")
        print(f"响应内容: {response.text}")
        
        if response.status_code == 200:
            result = response.json()
            if result.get("success"):
                print("Excel报告生成成功!")
            else:
                print(f"Excel报告生成失败: {result.get('message')}")
        else:
            print(f"请求失败，状态码: {response.status_code}")
            
    except Exception as e:
        print(f"请求出错: {e}")
    
    # 等待一段时间再测试HTML报告
    time.sleep(2)
    
    # 测试HTML报告生成
    print("\n测试HTML报告生成...")
    payload = {"format": "html"}
    
    try:
        response = requests.post(url, data=json.dumps(payload), headers=headers)
        print(f"响应状态码: {response.status_code}")
        print(f"响应内容: {response.text}")
        
        if response.status_code == 200:
            result = response.json()
            if result.get("success"):
                print("HTML报告生成成功!")
            else:
                print(f"HTML报告生成失败: {result.get('message')}")
        else:
            print(f"请求失败，状态码: {response.status_code}")
            
    except Exception as e:
        print(f"请求出错: {e}")

if __name__ == "__main__":
    test_report_generation()