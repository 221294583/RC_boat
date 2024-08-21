#include <Servo.h>
#include <SPI.h>
#include "WiFiS3.h"
//#include <WiFiNINA.h>
//#include <WiFiUdp.h>

char ssid[] = "EzTest";
char pass[] = "testarduino123";
int status = WL_IDLE_STATUS;
IPAddress gateway;
bool getRemote = false;
bool confirmed = false;
//int status;

WiFiUDP Udp;
unsigned int localPort = 2400;
unsigned int remotePort = 3200;
char packetBuffer[256];
char ReplyBuffer[] = "cced";

Servo myservo;
int pulse_width = 0;
int forward = 0;
int pos = 90;
bool smoke = false;
//电机接口3/6+7
//伺服电机接口9
//发烟器接口7
void setup() {
  Serial.begin(9600);
  pinMode(4,OUTPUT);
  pinMode(5,OUTPUT);
  pinMode(8,OUTPUT);

  myservo.attach(9);

  enable_WiFi();
  connect_WiFi();

  printWifiStatus();
  Udp.begin(localPort);
}

void loop() {
  //Serial.println("loop");

  if (!confirmed) {
    UDPsend();
  }

  UDPget();

  if (WiFi.status() != WL_CONNECTED) {
    connect_WiFi();
    confirmed = false;
  }
}

void UDPget() {
  int code = 0;
  int packetSize = Udp.parsePacket();
  if (packetSize) {
    Serial.print("Received packet of size ");
    Serial.println(packetSize);
    Serial.print("From ");
    IPAddress remoteIp = Udp.remoteIP();
    Serial.print(remoteIp);
    Serial.print(", port ");
    Serial.println(Udp.remotePort());
    // read the packet into packetBufffer
    int len = Udp.read(packetBuffer, 255);
    if (len > 0) {
      packetBuffer[len] = 0;
    }
    Serial.println("Contents:");
    Serial.println(packetBuffer);
    if (packetBuffer[0] == 's') {
      smoke = (!smoke);
      digitalWrite(8, smoke);
    } else if (packetBuffer[0] == 'r') {
      forward = 0;
      pulse_width = 0;
      pos = 90;
    } else if (packetBuffer[0] == 'c') {
      confirmed = true;
    } else if ((packetBuffer[0] == '+') || (packetBuffer[0] == '-')) {
      if (packetBuffer[0] == '+') {
        forward = 1;
      }
      if (packetBuffer[0] == '-') {
        forward = 2;
      }
      pulse_width = toInt(packetBuffer, 1);
      pulse_width = max(pulse_width,154);
      pos = toInt(packetBuffer, 2);
      if (pos > 180) {
        pos = 360 - pos;
      }
    }
    Serial.println(forward);
    if (forward == 0) {
      analogWrite(3, 0);
      digitalWrite(4 , 0);
      digitalWrite(5, 0);
    } else if (forward == 1) {
      analogWrite(3, pulse_width);
      digitalWrite(4, HIGH);
      digitalWrite(5, LOW);
      } else if (forward == 2) {
      analogWrite(3, pulse_width);
      digitalWrite(4, LOW);
      digitalWrite(5, HIGH);
    }
    myservo.write(((pos - 90) / 5) + 90);
    Serial.println(pos);
    Serial.println(pulse_width);
    // send a reply, to the IP address and port that sent us the packet we received
    Udp.beginPacket(gateway, remotePort);
    Udp.write(ReplyBuffer);
    Udp.endPacket();
    Serial.println("reply");
  }
}

void UDPsend() {
  Udp.beginPacket(gateway, remotePort);
  Udp.write(ReplyBuffer);
  Udp.endPacket();
  Serial.println("sent");
  delay(2000);
}

void printWifiStatus() {
  // print the SSID of the network you're attached to:
  Serial.print("SSID: ");
  Serial.println(WiFi.SSID());

  // print your board's IP address:
  IPAddress ip = WiFi.localIP();
  Serial.print("IP Address: ");
  Serial.println(ip);

  // print the received signal strength:
  long rssi = WiFi.RSSI();
  Serial.print("signal strength (RSSI):");
  Serial.print(rssi);
  Serial.println(" dBm");

  Serial.print("To see this page in action, open a browser to http://");
  Serial.println(ip);
}

void enable_WiFi() {
  // check for the WiFi module:
  if (WiFi.status() == WL_NO_MODULE) {
    Serial.println("Communication with WiFi module failed!");
    // don't continue
    while (true)
      ;
  }

  String fv = WiFi.firmwareVersion();
  if (fv < "1.0.0") {
    Serial.println("Please upgrade the firmware");
  }
}

void connect_WiFi() {
  // attempt to connect to Wifi network:
  while (status != WL_CONNECTED) {
    Serial.print("Attempting to connect to SSID: ");
    Serial.println(ssid);
    // Connect to WPA/WPA2 network. Change this line if using open or WEP network:
    status = WiFi.begin(ssid, pass);
    delay(10000);
    if (!getRemote) {
      gateway = WiFi.gatewayIP();
      Serial.print("remote IP Address: ");
      Serial.println(gateway);
      IPAddress ip(gateway[0], gateway[1], gateway[2], 99);
      WiFi.config(ip);
      WiFi.disconnect();
      getRemote = true;
    }
    status = WiFi.begin(ssid, pass);
    delay(10000);
    //WiFi.begin(ssid, pass);
    // wait 10 seconds for connection:
  }
}

int toInt(char* src, int seq) {
  char result[3];
  int start = 1;

  if (seq == 2) {
    start = 5;
  }
  for (int i = start; i < start + 3; i++) {
    result[i - start] = src[i];
  }

  return atoi(result);
}