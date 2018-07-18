# 부산버스 관리시스템



## Account Info



### Firebase

ID   : hutechbusanbus@gmail.com

PW : inha123qwe



### Google

ID   : hutechbusanbus@gmail.com

PW : inha123qwe



### Facebook

ID   : hutechbusanbus@gmail.com

PW : inha123qwe



### Kakao

ID   : rex@hutek.net

PW : hutek02!@



## DB Table Info



### Sequences

- Auto_increment sequences

| ColumnName     | DataType | Option         |
| -------------- | -------- | -------------- |
| userSeq        | int      | AUTO_INCREMENT |
| adminSeq       | int      | AUTO_INCREMENT |
| groupUserSeq   | int      | AUTO_INCREMENT |
| clientGroupSeq | int      | AUTO_INCREMENT |



### User

- 사용자(버스기사) 정보 테이블

| ColumnName | DataType | Option                   |
| ---------- | -------- | ------------------------ |
| userNo     | int      | Sequences -> userSeq + 1 |
| id         | string   |                          |
| pw         | string   |                          |
| phone      | string   | 미입력시 '0'             |
| kakaoID    | string   | 미입력시 '0'             |
| facebookID | string   | 미입력시 '0'             |



### UserLoginData

- 유저 로그인 기록

| ColumnName | DataType | Option                   |
| ---------- | -------- | ------------------------ |
| id         | string   |                          |
| time       | string   |                          |
| BLEstate   | int      | 로그인 관련 signal       |
| LoginState | int      | 1000: Logout 2000: Login |



### UserDriveData

- 유저 운행 기록

| ColumnName | DataType | Option                               |
| ---------- | -------- | ------------------------------------ |
| id         | string   |                                      |
| time       | string   |                                      |
| BLEstate   | int      | 운행 관련 signal                     |
| DriveState | int      | 2000: Login(stop) 2100: Login(drive) |



### Admin

- 관리자 정보 테이블

| ColumnName | DataType | Option                    |
| ---------- | -------- | ------------------------- |
| adminNo    | int      | Sequences -> adminSeq + 1 |
| id         | string   |                           |
| pw         | string   |                           |



### GroupUser

- 단체사용자(버스회사) 테이블

| ColumnName | DataType | Option                        |
| ---------- | -------- | ----------------------------- |
| groupNo    | int      | Sequences -> groupUserSeq + 1 |
| groupName  | string   |                               |
| manager    | string   |                               |
| email      | string   |                               |
| phone      | string   |                               |



### ClientGroup

- 고객사(?) 테이블

| ColumnName | DataType | Option                          |
| ---------- | -------- | ------------------------------- |
| cilentNo   | int      | Sequences -> clientGroupSeq + 1 |
| clientName | string   |                                 |
| manager    | string   |                                 |
| email      | string   |                                 |
| phone      | string   |                                 |

  

