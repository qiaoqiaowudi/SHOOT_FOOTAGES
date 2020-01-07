import os
import socket
from multiprocessing.context import Process


def handle(visit_socket):
    request_sum = '-+'
    while True:
        request_data = visit_socket.recv(1)
        request_1 = str(request_data,encoding= "UTF-8")
        print(request_1)
        request_sum = request_sum + request_1
        if request_1 == '+':
            request_sum =request_sum+'-'
            a, file_size, b = request_sum.split('+')
            print(a)
            print(b)
            break
    file_size = int(file_size)
    print('file_size:')
    print(file_size)
    BASE_DIR = os.path.dirname(os.path.abspath(__file__))
    path = os.path.join(BASE_DIR,'received.mp4')
    has_sent = 0

    with open(path,'wb')as fp:
        while has_sent != file_size:
            data = visit_socket.recv(1024)
            fp.write(data)
            has_sent+=len(data)
            print('\r'+'[保存进度]:%s%.02f%%' % ('>'*int((has_sent/file_size)*50), float(has_sent/file_size)*100),end='')
    print('保存成功')




if __name__ == "__main__":
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)  # 创建服务器的socket
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server_socket.bind(("", 8000))  # 设置自己的服务端口
    server_socket.listen(128)  # 实现监听

    while True:
        visit_socket, visit_address = server_socket.accept()
        print("%s %s已将链接" % visit_address)
        handle_process = Process(target=handle, args=(visit_socket,))
        handle_process.start(),
        visit_socket.close()
