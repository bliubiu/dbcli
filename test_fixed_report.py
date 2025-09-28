import requests
import json
import time

def test_html_report_generation():
    url = "http://localhost:8080/api/generate-report"
    
    # 测试HTML报告生成
    print("测试HTML报告生成...")
    payload = {"format": "html"}
    headers = {"Content-Type": "application/json"}
    
    try:
        response = requests.post(url, data=json.dumps(payload), headers=headers)
        print(f"响应状态码: {response.status_code}")
        if response.status_code == 200:
            result = response.json()
            print(f"响应内容: {json.dumps(result, indent=2, ensure_ascii=False)}")
            if result.get("success"):
                print("HTML报告生成成功!")
                if "fileName" in result:
                    print(f"生成的文件: {result['fileName']}")
                if "previewUrl" in result:
                    print(f"预览URL: {result['previewUrl']}")
                return True
            else:
                print(f"HTML报告生成失败: {result.get('message')}")
        else:
            print(f"请求失败，状态码: {response.status_code}")
            print(f"响应内容: {response.text}")
            
    except Exception as e:
        print(f"请求出错: {e}")
        return False
    
    return False

def test_excel_report_generation():
    url = "http://localhost:8080/api/generate-report"
    
    # 测试Excel报告生成
    print("\n测试Excel报告生成...")
    payload = {"format": "excel"}
    headers = {"Content-Type": "application/json"}
    
    try:
        response = requests.post(url, data=json.dumps(payload), headers=headers)
        print(f"响应状态码: {response.status_code}")
        if response.status_code == 200:
            result = response.json()
            print(f"响应内容: {json.dumps(result, indent=2, ensure_ascii=False)}")
            if result.get("success"):
                print("Excel报告生成成功!")
                if "fileName" in result:
                    print(f"生成的文件: {result['fileName']}")
                if "previewUrl" in result:
                    print(f"预览URL: {result['previewUrl']}")
                return True
            else:
                print(f"Excel报告生成失败: {result.get('message')}")
        else:
            print(f"请求失败，状态码: {response.status_code}")
            print(f"响应内容: {response.text}")
            
    except Exception as e:
        print(f"请求出错: {e}")
        return False
    
    return False

if __name__ == "__main__":
    html_success = test_html_report_generation()
    excel_success = test_excel_report_generation()
    
    if html_success and excel_success:
        print("\n所有测试通过！")
    else:
        print("\n部分测试失败！")