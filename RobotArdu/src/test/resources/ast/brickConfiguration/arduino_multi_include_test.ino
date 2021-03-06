// This file is automatically generated by the Open Roberta Lab.

#include <math.h>
#include <Servo.h>
#include <SPI.h>
#include <MFRC522.h>
#include <RobertaFunctions.h>   // Open Roberta library
#include <NEPODefs.h>

RobertaFunctions rob;

String ___item;
bool ___item2;
Servo _servo_S;
#define SS_PIN_R6 10
#define RST_PIN_R6 9
MFRC522 _mfrc522_R6(SS_PIN_R6, RST_PIN_R6);
#define SS_PIN_R7 10
#define RST_PIN_R7 9
MFRC522 _mfrc522_R7(SS_PIN_R7, RST_PIN_R7);
Servo _servo_S2;
String _readRFIDData()
{
    if(!_mfrc522_R6.PICC_IsNewCardPresent()) 
    {
        return "N/A";
    }
    if(!_mfrc522_R6.PICC_ReadCardSerial())
    {
        return "N/D";
    }
    return String(((long)(_mfrc522_R6.uid.uidByte[0])<<24)
    |((long)(_mfrc522_R6.uid.uidByte[1])<<16)
    | ((long)(_mfrc522_R6.uid.uidByte[2])<<8)
    | ((long)_mfrc522_R6.uid.uidByte[3]), HEX);
}

void setup()
{
    Serial.begin(9600); 
    _servo_S.attach(8);
    SPI.begin();
    _mfrc522_R6.PCD_Init();
    SPI.begin();
    _mfrc522_R7.PCD_Init();
    _servo_S2.attach(8);
    ___item = "";
    ___item2 = true;
}

void loop()
{
    _servo_S.write(90);
    _servo_S.write(90);
    ___item = _readRFIDData();
    ___item = _readRFIDData();
}
