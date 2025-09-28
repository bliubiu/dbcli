import requests
import time
import json

# 测试实时日志功能
def test_real_time_logs():
    try:
        # 发送请求到日志API
        response = requests.get('http://localhost:8080/api/logs', timeout=10)
        print(f"响应状态码: {response.status_code}")
        
        if response.status_code == 200:
            try:
                # 尝试解析JSON
                logs_data = response.json()
                print("日志API响应 (JSON解析成功):")
                
                # 检查是否有日志内容
                if 'logs' in logs_data:
                    print(f"获取到 {len(logs_data['logs'])} 条日志")
                    # 显示最后5条日志
                    for i, log in enumerate(logs_data['logs'][-5:]):
                        print(f"  {i+1}. {log}")
                else:
                    print("响应中没有找到日志数据")
                    # 打印部分响应内容用于调试
                    print("响应内容预览:")
                    print(str(response.text)[:200])
            except json.JSONDecodeError as e:
                print(f"JSON解析失败: {e}")
                print("原始响应内容:")
                print(response.text[:500])
        else:
            print(f"请求失败，状态码: {response.status_code}")
            print("响应内容:")
            print(response.text[:500])
    except requests.exceptions.RequestException as e:
        print(f"网络请求错误: {e}")
    except Exception as e:
        print(f"测试过程中出现错误: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    print("测试实时日志功能...")
    test_real_time_logs()