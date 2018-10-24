package de.fhg.iais.roberta.visitor.codegen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import de.fhg.iais.roberta.components.Category;
import de.fhg.iais.roberta.components.Configuration;
import de.fhg.iais.roberta.components.ConfigurationComponent;
import de.fhg.iais.roberta.components.UsedSensor;
import de.fhg.iais.roberta.mode.action.MotorMoveMode;
import de.fhg.iais.roberta.syntax.BlocklyConstants;
import de.fhg.iais.roberta.syntax.Phrase;
import de.fhg.iais.roberta.syntax.SC;
import de.fhg.iais.roberta.syntax.action.display.ClearDisplayAction;
import de.fhg.iais.roberta.syntax.action.display.ShowTextAction;
import de.fhg.iais.roberta.syntax.action.light.LightAction;
import de.fhg.iais.roberta.syntax.action.light.LightStatusAction;
import de.fhg.iais.roberta.syntax.action.motor.MotorOnAction;
import de.fhg.iais.roberta.syntax.action.sound.ToneAction;
import de.fhg.iais.roberta.syntax.actors.arduino.PinWriteValueAction;
import de.fhg.iais.roberta.syntax.actors.arduino.RelayAction;
import de.fhg.iais.roberta.syntax.actors.arduino.SerialWriteAction;
import de.fhg.iais.roberta.syntax.lang.blocksequence.MainTask;
import de.fhg.iais.roberta.syntax.lang.expr.Expr;
import de.fhg.iais.roberta.syntax.lang.expr.RgbColor;
import de.fhg.iais.roberta.syntax.sensor.generic.DropSensor;
import de.fhg.iais.roberta.syntax.sensor.generic.EncoderSensor;
import de.fhg.iais.roberta.syntax.sensor.generic.HumiditySensor;
import de.fhg.iais.roberta.syntax.sensor.generic.InfraredSensor;
import de.fhg.iais.roberta.syntax.sensor.generic.KeysSensor;
import de.fhg.iais.roberta.syntax.sensor.generic.LightSensor;
import de.fhg.iais.roberta.syntax.sensor.generic.MoistureSensor;
import de.fhg.iais.roberta.syntax.sensor.generic.MotionSensor;
import de.fhg.iais.roberta.syntax.sensor.generic.PinGetValueSensor;
import de.fhg.iais.roberta.syntax.sensor.generic.PulseSensor;
import de.fhg.iais.roberta.syntax.sensor.generic.RfidSensor;
import de.fhg.iais.roberta.syntax.sensor.generic.TemperatureSensor;
import de.fhg.iais.roberta.syntax.sensor.generic.UltrasonicSensor;
import de.fhg.iais.roberta.syntax.sensor.generic.VoltageSensor;
import de.fhg.iais.roberta.util.dbc.DbcException;
import de.fhg.iais.roberta.visitor.IVisitor;
import de.fhg.iais.roberta.visitor.collect.ArduinoUsedHardwareCollectorVisitor;
import de.fhg.iais.roberta.visitor.hardware.IArduinoVisitor;

/**
 * This class is implementing {@link IVisitor}. All methods are implemented and they append a human-readable C representation of a phrase to a StringBuilder.
 * <b>This representation is correct C code for Arduino.</b> <br>
 */
public final class ArduinoCppVisitor extends AbstractCommonArduinoCppVisitor implements IArduinoVisitor<Void> {
    private final boolean isTimerSensorUsed;
    private final boolean isListsUsed;

    /**
     * Initialize the C++ code generator visitor.
     *
     * @param programPhrases to generate the code from
     * @param indentation to start with. Will be incr/decr depending on block structure
     */
    private ArduinoCppVisitor(Configuration brickConfiguration, ArrayList<ArrayList<Phrase<Void>>> phrases, int indentation) {
        super(brickConfiguration, phrases, indentation);
        ArduinoUsedHardwareCollectorVisitor codePreprocessVisitor = new ArduinoUsedHardwareCollectorVisitor(phrases, brickConfiguration);
        this.usedSensors = codePreprocessVisitor.getUsedSensors();
        this.usedVars = codePreprocessVisitor.getVisitedVars();
        //TODO: fix how the timer is detected for all robots
        this.isTimerSensorUsed = codePreprocessVisitor.isTimerSensorUsed();
        this.loopsLabels = codePreprocessVisitor.getloopsLabelContainer();
        this.isListsUsed = codePreprocessVisitor.isListsUsed();
    }

    /**
     * factory method to generate C++ code from an AST.<br>
     *
     * @param brickConfiguration
     * @param programPhrases to generate the code from
     * @param withWrapping if false the generated code will be without the surrounding configuration code
     */
    public static String generate(Configuration brickConfiguration, ArrayList<ArrayList<Phrase<Void>>> programPhrases, boolean withWrapping) {
        ArduinoCppVisitor astVisitor = new ArduinoCppVisitor(brickConfiguration, programPhrases, withWrapping ? 1 : 0);
        astVisitor.generateCode(withWrapping);
        return astVisitor.sb.toString();
    }

    @Override
    public Void visitShowTextAction(ShowTextAction<Void> showTextAction) {
        this.sb.append("_lcd_" + showTextAction.getPort() + ".setCursor(");
        showTextAction.getX().visit(this);
        this.sb.append(",");
        showTextAction.getY().visit(this);
        this.sb.append(");");
        nlIndent();
        this.sb.append("_lcd_" + showTextAction.getPort() + ".print(");
        showTextAction.getMsg().visit(this);
        this.sb.append(");");
        return null;
    }

    @Override
    public Void visitClearDisplayAction(ClearDisplayAction<Void> clearDisplayAction) {
        this.sb.append("_lcd_" + clearDisplayAction.getPort() + ".clear();");
        return null;
    }

    @Override
    public Void visitLightAction(LightAction<Void> lightAction) {
        if ( !lightAction.getMode().toString().equals(BlocklyConstants.DEFAULT) ) {
            this.sb.append("digitalWrite(_led_" + lightAction.getPort() + ", " + lightAction.getMode().getValues()[0] + ");");
        } else {
            Map<String, Expr<Void>> Channels = new HashMap<>();
            Channels.put("red", ((RgbColor<Void>) lightAction.getRgbLedColor()).getR());
            Channels.put("green", ((RgbColor<Void>) lightAction.getRgbLedColor()).getG());
            Channels.put("blue", ((RgbColor<Void>) lightAction.getRgbLedColor()).getB());
            Channels.forEach((k, v) -> {
                this.sb.append("analogWrite(_led_" + k + "_" + lightAction.getPort() + ", ");
                v.visit(this);
                this.sb.append(");");
                nlIndent();
            });
        }
        return null;
    }

    @Override
    public Void visitLightStatusAction(LightStatusAction<Void> lightStatusAction) {
        String[] colors =
            {
                "red",
                "green",
                "blue"
            };
        for ( int i = 0; i < 3; i++ ) {
            this.sb.append("analogWrite(_led_" + colors[i] + "_" + lightStatusAction.getPort() + ", 0);");
            nlIndent();
        }
        return null;
    }

    @Override
    public Void visitToneAction(ToneAction<Void> toneAction) {
        //9 - sound port
        this.sb.append("tone(_spiele_" + toneAction.getPort() + ",");
        toneAction.getFrequency().visit(this);
        this.sb.append(", ");
        toneAction.getDuration().visit(this);
        this.sb.append(");");
        return null;
    }

    @Override
    public Void visitMotorOnAction(MotorOnAction<Void> motorOnAction) {
        boolean step = motorOnAction.getParam().getDuration() != null;
        if ( step ) {//step motor
            this.sb.append("Motor_" + motorOnAction.getUserDefinedPort() + ".setSpeed(");
            motorOnAction.getParam().getSpeed().visit(this);
            this.sb.append(");");
            nlIndent();
            this.sb.append("Motor_" + motorOnAction.getUserDefinedPort() + ".step(_SPU_" + motorOnAction.getUserDefinedPort() + "*");
            motorOnAction.getDurationValue().visit(this);
            if ( motorOnAction.getDurationMode().equals(MotorMoveMode.DEGREE) ) {
                this.sb.append("/360");
            }
            this.sb.append(");");
        } else {//servo motor
            this.sb.append("_servo_" + motorOnAction.getUserDefinedPort() + ".write(");
            motorOnAction.getParam().getSpeed().visit(this);
            this.sb.append(");");
        }
        return null;
    }

    @Override
    public Void visitRelayAction(RelayAction<Void> relayAction) {
        this.sb.append("digitalWrite(_relay_").append(relayAction.getPort()).append(", ").append(relayAction.getMode().getValues()[0]).append(");");
        return null;
    }

    @Override
    public Void visitLightSensor(LightSensor<Void> lightSensor) {
        this.sb.append("analogRead(_output_" + lightSensor.getPort() + ")/10.24");
        return null;
    }

    @Override
    public Void visitKeysSensor(KeysSensor<Void> button) {
        this.sb.append("digitalRead(_taster_" + button.getPort() + ")");
        return null;
    }

    public void measureDistanceUltrasonicSensor(String sensorName) {
        this.sb.append("double _getUltrasonicDistance()");
        this.nlIndent();
        this.sb.append("{");
        this.incrIndentation();
        this.nlIndent();
        this.sb.append("digitalWrite(_trigger_" + sensorName + ", LOW);");
        nlIndent();
        this.sb.append("delay(5);");
        nlIndent();
        this.sb.append("digitalWrite(_trigger_" + sensorName + ", HIGH);");
        nlIndent();
        this.sb.append("delay(10);");
        nlIndent();
        this.sb.append("digitalWrite(_trigger_" + sensorName + ", LOW);");
        nlIndent();
        this.sb.append("return pulseIn(_echo_" + sensorName + ", HIGH)*_signalToDistance;");
        this.decrIndentation();
        this.nlIndent();
        this.sb.append("}");
        this.nlIndent();
    }

    @Override
    public Void visitUltrasonicSensor(UltrasonicSensor<Void> ultrasonicSensor) {
        this.sb.append("_getUltrasonicDistance()");
        return null;
    }

    @Override
    public Void visitMoistureSensor(MoistureSensor<Void> moistureSensor) {
        this.sb.append("analogRead(_moisturePin_" + moistureSensor.getPort() + ")/10.24");
        return null;
    }

    @Override
    public Void visitTemperatureSensor(TemperatureSensor<Void> temperatureSensor) {
        this.sb.append("map(analogRead(_TMP36_" + temperatureSensor.getPort() + "), 0, 410, -50, 150)");
        return null;
    }

    @Override
    public Void visitEncoderSensor(EncoderSensor<Void> encoderSensor) {
        this.sb.append("meinEncoder.read()");
        return null;
    }

    @Override
    public Void visitVoltageSensor(VoltageSensor<Void> potentiometer) {
        this.sb.append("((double)analogRead(_output_" + potentiometer.getPort() + "))*5/1024");
        return null;
    }

    @Override
    public Void visitHumiditySensor(HumiditySensor<Void> humiditySensor) {
        switch ( humiditySensor.getMode() ) {
            case SC.HUMIDITY:
                this.sb.append("_dht_" + humiditySensor.getPort() + ".readHumidity()");
                break;
            case SC.TEMPERATURE:
                this.sb.append("_dht_" + humiditySensor.getPort() + ".readTemperature()");
                break;
            default:
                throw new DbcException("Invalide mode for Humidity Sensor!");
        }
        return null;
    }

    @Override
    public Void visitDropSensor(DropSensor<Void> dropSensor) {
        this.sb.append("analogRead(_S_" + dropSensor.getPort() + ")/10.24");
        return null;
    }

    private void generatePinGetValue(UsedSensor usedSensor) {
        this.sb.append("double _pinGetValue(int pinName, int mode) {");
        incrIndentation();
        nlIndent();
        this.sb.append("pinMode(pinName, INPUT);");
        nlIndent();
        this.sb.append("switch ( mode ) {");
        nlIndent();
        incrIndentation();
        this.sb.append("case _ANALOG:");
        nlIndent();
        this.sb.append("return (double) analogRead(pinName);");
        decrIndentation();
        nlIndent();
        incrIndentation();
        this.sb.append("case _DIGITAL:");
        nlIndent();
        this.sb.append("return (double) digitalRead(pinName);");
        decrIndentation();
        nlIndent();
        this.sb.append("default:");
        incrIndentation();
        nlIndent();
        this.sb.append("return -1.0;");
        decrIndentation();
        nlIndent();
        decrIndentation();
        this.sb.append("}");
        nlIndent();
        this.sb.append("}");
        nlIndent();
    }

    @Override
    public Void visitPinGetValueSensor(PinGetValueSensor<Void> pinGetValueSensor) {
        this.sb.append("_pinGetValue(" + pinGetValueSensor.getPort() + ", _" + pinGetValueSensor.getMode() + ")");
        return null;
    }

    @Override
    public Void visitPulseSensor(PulseSensor<Void> pulseSensor) {
        this.sb.append("analogRead(_SensorPin_" + pulseSensor.getPort() + ")");
        return null;
    }

    public Void readRFIDData(String sensorName) {
        this.sb.append("String _readRFIDData()");
        this.nlIndent();
        this.sb.append("{");
        incrIndentation();
        nlIndent();
        this.sb.append("if(!_mfrc522_" + sensorName + ".PICC_IsNewCardPresent()) ");
        nlIndent();
        this.sb.append("{");
        incrIndentation();
        nlIndent();
        this.sb.append("return \"N/A\";");
        decrIndentation();
        nlIndent();
        this.sb.append("}");
        nlIndent();
        this.sb.append("if(!_mfrc522_R.PICC_ReadCardSerial())");
        nlIndent();
        this.sb.append("{");
        incrIndentation();
        nlIndent();
        this.sb.append("return \"N/D\";");
        decrIndentation();
        nlIndent();
        this.sb.append("}");
        nlIndent();
        this.sb
            .append(
                "return String(((long)(_mfrc522_"
                    + sensorName
                    + ".uid.uidByte[0])<<24)\n    |((long)(_mfrc522_"
                    + sensorName
                    + ".uid.uidByte[1])<<16)\n    | ((long)(_mfrc522_"
                    + sensorName
                    + ".uid.uidByte[2])<<8)\n    | ((long)_mfrc522_"
                    + sensorName
                    + ".uid.uidByte[3]), HEX);");

        decrIndentation();
        this.nlIndent();
        this.sb.append("}");
        this.nlIndent();
        return null;

    }

    @Override
    public Void visitRfidSensor(RfidSensor<Void> rfidSensor) {
        switch ( rfidSensor.getMode() ) {
            case SC.PRESENCE:
                this.sb.append("_mfrc522_" + rfidSensor.getPort() + ".PICC_IsNewCardPresent()");
                break;
            case SC.SERIAL:
                this.sb.append("_readRFIDData()");
                break;
            default:
                throw new DbcException("Invalide mode for RFID Sensor!");
        }
        return null;
    }

    public void measureIRValue(UsedSensor infraredSensor) {
        switch ( infraredSensor.getMode() ) {
            case SC.PRESENCE:
                this.sb.append("bool _getIRResults()\n{");
                incrIndentation();
                nlIndent();
                this.sb.append("bool results = false;");
                nlIndent();
                this.sb.append("if (_irrecv_" + infraredSensor.getPort() + ".decode(&_results_" + infraredSensor.getPort() + ")) {");
                incrIndentation();
                nlIndent();
                this.sb.append("results = true;");
                nlIndent();
                this.sb.append("_irrecv_" + infraredSensor.getPort() + ".resume();");
                decrIndentation();
                nlIndent();
                this.sb.append("}");
                break;
            case SC.VALUE:
                this.sb.append("long int _getIRResults()\n{");
                incrIndentation();
                nlIndent();
                this.sb.append("long int results = 0;");
                nlIndent();
                this.sb.append("if (_irrecv_" + infraredSensor.getPort() + ".decode(&_results_" + infraredSensor.getPort() + ")) {");
                incrIndentation();
                nlIndent();
                this.sb.append("results = _results_" + infraredSensor.getPort() + ".value;");
                nlIndent();
                this.sb.append("_irrecv_" + infraredSensor.getPort() + ".resume();");
                decrIndentation();
                nlIndent();
                this.sb.append("}");
                break;
            default:
                throw new DbcException("Invalide mode for IR Sensor!");
        }
        nlIndent();
        this.sb.append("return results;");
        decrIndentation();
        nlIndent();
        this.sb.append("}");
        nlIndent();
    }

    @Override
    public Void visitInfraredSensor(InfraredSensor<Void> infraredSensor) {
        this.sb.append("_getIRResults()");
        return null;
    }

    @Override
    public Void visitMotionSensor(MotionSensor<Void> motionSensor) {
        this.sb.append("digitalRead(_output_" + motionSensor.getPort() + ")");
        return null;
    }

    @Override
    public Void visitMainTask(MainTask<Void> mainTask) {

        mainTask.getVariables().visit(this);
        nlIndent();
        generateConfigurationVariables();
        if ( this.isTimerSensorUsed ) {
            this.sb.append("unsigned long __time = millis();");
            this.nlIndent();
        }
        long numberConf =
            this.programPhrases
                .stream()
                .filter(phrase -> (phrase.getKind().getCategory() == Category.METHOD) && !phrase.getKind().hasName("METHOD_CALL"))
                .count();
        if ( (this.configuration.getConfigurationComponents().isEmpty() || this.isTimerSensorUsed) && (numberConf == 0) ) {
            this.nlIndent();
        }
        generateUserDefinedMethods();
        if ( numberConf != 0 ) {
            nlIndent();
        }
        for ( UsedSensor usedSensor : this.usedSensors ) {
            if ( usedSensor.getType().equals(SC.INFRARED) ) {
                this.measureIRValue(usedSensor);
                this.nlIndent();
                break;
            }
        }
        for ( UsedSensor usedSensor : this.usedSensors ) {
            if ( usedSensor.getType().equals(SC.PIN_VALUE) ) {
                this.generatePinGetValue(usedSensor);
                this.nlIndent();
                break;
            }
        }
        for ( ConfigurationComponent usedConfigurationBlock : this.configuration.getConfigurationComponents() ) {
            if ( usedConfigurationBlock.getComponentType().equals(SC.ULTRASONIC) ) {
                this.measureDistanceUltrasonicSensor(usedConfigurationBlock.getUserDefinedPortName());
                this.nlIndent();
                break;
            }
        }
        for ( ConfigurationComponent usedConfigurationBlock : this.configuration.getConfigurationComponents() ) {
            if ( usedConfigurationBlock.getComponentType().equals(SC.RFID) ) {
                this.readRFIDData(usedConfigurationBlock.getUserDefinedPortName());
                this.nlIndent();
                break;
            }
        }
        this.sb.append("void setup()");
        this.nlIndent();
        this.sb.append("{");
        incrIndentation();
        nlIndent();
        this.sb.append("Serial.begin(9600); ");
        nlIndent();
        this.generateConfigurationSetup();
        this.generateUsedVars();
        this.sb.delete(this.sb.lastIndexOf("\n"), this.sb.length());
        this.decrIndentation();
        this.nlIndent();
        this.sb.append("}");
        this.nlIndent();
        return null;
    }

    @Override
    protected void generateProgramPrefix(boolean withWrapping) {
        if ( !withWrapping ) {
            return;
        } else {
            this.decrIndentation();
        }
        this.sb.append("// This file is automatically generated by the Open Roberta Lab.");
        this.nlIndent();
        this.nlIndent();
        this.sb.append("#include <math.h>");
        this.nlIndent();
        for ( ConfigurationComponent usedConfigurationBlock : this.configuration.getConfigurationComponents() ) {
            switch ( usedConfigurationBlock.getComponentType() ) {
                case SC.HUMIDITY:
                    this.sb.append("#include <DHT.h>");
                    this.nlIndent();
                    break;
                case SC.INFRARED:
                    this.sb.append("#include <IRremote.h>");
                    this.nlIndent();
                    break;
                case SC.ENCODER:
                    this.sb.append("#include <Encoder.h>");
                    this.nlIndent();
                    break;
                case SC.RFID:
                    this.sb.append("#include <SPI.h>");
                    this.nlIndent();
                    this.sb.append("#include <MFRC522.h>");
                    this.nlIndent();
                    break;
                case SC.LCD:
                    this.sb.append("#include <LiquidCrystal.h>");
                    this.nlIndent();
                    break;
                case SC.LCDI2C:
                    this.sb.append("#include <LiquidCrystal_I2C.h>");
                    this.nlIndent();
                    break;
                case SC.STEPMOTOR:
                    this.sb.append("#include <Stepper.h>");
                    this.nlIndent();
                    break;
                case SC.SERVOMOTOR:
                    this.sb.append("#include <Servo.h>");
                    this.nlIndent();
                    break;
                case SC.ULTRASONIC:
                case SC.MOTION:
                case SC.MOISTURE:
                case SC.KEY:
                case SC.LIGHT:
                case SC.POTENTIOMETER:
                case SC.TEMPERATURE:
                case SC.DROP:
                case SC.PULSE:
                case SC.LED:
                case SC.RGBLED:
                case SC.BUZZER:
                case SC.RELAY:
                    break;
                default:
                    throw new DbcException("Sensor is not supported: " + usedConfigurationBlock.getComponentType());
            }
        }
        for ( UsedSensor usedSensor : this.usedSensors ) {
            switch ( usedSensor.getType() ) {
                case SC.PIN_VALUE:
                    this.sb.append("#define _ANALOG 0\n#define _DIGITAL 1\n");
                    break;
                default:
                    break;
            }
        }
        this.sb.append("#include <RobertaFunctions.h>   // Open Roberta library");
        this.nlIndent();
        if ( this.isListsUsed ) {
            this.sb.append("#include <ArduinoSTL.h>");
            nlIndent();
            this.sb.append("#include <list>");
            nlIndent();
        }
        this.sb.append("#include <NEPODefs.h>");
        this.nlIndent();
        this.nlIndent();
        this.sb.append("RobertaFunctions rob;");
    }

    @Override
    protected void generateProgramSuffix(boolean withWrapping) {
        // nothing to do because the arduino loop closes the program
    }

    private void generateConfigurationSetup() {
        for ( ConfigurationComponent usedConfigurationBlock : this.configuration.getConfigurationComponents() ) {
            switch ( usedConfigurationBlock.getComponentType() ) {
                case SC.HUMIDITY:
                    this.sb.append("_dht_" + usedConfigurationBlock.getUserDefinedPortName() + ".begin();");
                    nlIndent();
                    break;
                case SC.ULTRASONIC:
                    this.sb.append("pinMode(_trigger_" + usedConfigurationBlock.getUserDefinedPortName() + ", OUTPUT);");
                    nlIndent();
                    this.sb.append("pinMode(_echo_" + usedConfigurationBlock.getUserDefinedPortName() + ", INPUT);");
                    nlIndent();
                    break;
                case SC.MOTION:
                    this.sb.append("pinMode(_output_" + usedConfigurationBlock.getUserDefinedPortName() + ", INPUT);");
                    nlIndent();
                    break;
                case SC.MOISTURE:
                    break;
                case SC.INFRARED:
                    this.sb.append("pinMode(13, OUTPUT);");
                    nlIndent();
                    this.sb.append("_irrecv_" + usedConfigurationBlock.getUserDefinedPortName() + ".enableIRIn();");
                    nlIndent();
                    break;
                case SC.KEY:
                    this.sb.append("pinMode(_taster_" + usedConfigurationBlock.getUserDefinedPortName() + ", INPUT);");
                    nlIndent();
                case SC.LIGHT:
                    break;
                case SC.POTENTIOMETER:
                    break;
                case SC.TEMPERATURE:
                    break;
                case SC.ENCODER:
                    this.sb.append("pinMode(_SW_" + usedConfigurationBlock.getUserDefinedPortName() + ", INPUT);");
                    nlIndent();
                    this.sb.append("attachInterrupt(digitalPinToInterrupt(_SW_" + usedConfigurationBlock.getUserDefinedPortName() + "), Interrupt, CHANGE);");
                    nlIndent();
                    break;
                case SC.DROP:
                    break;
                case SC.PULSE:
                    break;
                case SC.RFID:
                    this.sb.append("SPI.begin();");
                    nlIndent();
                    this.sb.append("_mfrc522_" + usedConfigurationBlock.getUserDefinedPortName() + ".PCD_Init();");
                    nlIndent();
                    break;
                case SC.LCD:
                    this.sb.append("_lcd_" + usedConfigurationBlock.getUserDefinedPortName() + ".begin(16, 2);");
                    nlIndent();
                    break;
                case SC.LCDI2C:
                    this.sb.append("_lcd_" + usedConfigurationBlock.getUserDefinedPortName() + ".begin();");
                    nlIndent();
                    break;
                case SC.LED:
                    this.sb.append("pinMode(_led_" + usedConfigurationBlock.getUserDefinedPortName() + ", OUTPUT);");
                    nlIndent();
                    break;
                case SC.RGBLED:
                    this.sb.append("pinMode(_led_red_" + usedConfigurationBlock.getUserDefinedPortName() + ", OUTPUT);");
                    nlIndent();
                    this.sb.append("pinMode(_led_green_" + usedConfigurationBlock.getUserDefinedPortName() + ", OUTPUT);");
                    nlIndent();
                    this.sb.append("pinMode(_led_blue_" + usedConfigurationBlock.getUserDefinedPortName() + ", OUTPUT);");
                    nlIndent();
                    break;
                case SC.BUZZER:
                    break;
                case SC.RELAY:
                    this.sb.append("pinMode(_relay_" + usedConfigurationBlock.getUserDefinedPortName() + ", OUTPUT);");
                    nlIndent();
                    break;
                case SC.STEPMOTOR:
                    break;
                case SC.SERVOMOTOR:
                    this.sb.append("_servo_" + usedConfigurationBlock.getUserDefinedPortName() + ".attach(" + usedConfigurationBlock.getPortName() + ");");
                    nlIndent();
                    break;
                default:
                    throw new DbcException("Sensor is not supported: " + usedConfigurationBlock.getComponentType());
            }
        }
    }

    private void generateConfigurationVariables() {
        for ( ConfigurationComponent cc : this.configuration.getConfigurationComponents() ) {
            String blockName = cc.getUserDefinedPortName();
            switch ( cc.getComponentType() ) {
                case SC.HUMIDITY:
                    this.sb.append("#define DHTPIN" + blockName + " ").append(cc.getProperty("OUTPUT"));
                    nlIndent();
                    this.sb.append("#define DHTTYPE DHT11");
                    nlIndent();
                    this.sb.append("DHT _dht_" + blockName + "(DHTPIN" + blockName + ", DHTTYPE);");
                    nlIndent();
                    break;
                case SC.ULTRASONIC:
                    this.sb.append("int _trigger_" + blockName + " = ").append(cc.getProperty("TRIG")).append(";");
                    nlIndent();
                    this.sb.append("int _echo_" + blockName + " = ").append(cc.getProperty("ECHO")).append(";");
                    nlIndent();
                    this.sb.append("double _signalToDistance = 0.03432/2;");
                    nlIndent();
                    break;
                case SC.MOISTURE:
                    this.sb.append("int _moisturePin_" + blockName + " = ").append(cc.getProperty("S")).append(";");
                    nlIndent();
                    break;
                case SC.INFRARED:
                    this.sb.append("int _RECV_PIN_" + blockName + " = ").append(cc.getProperty("OUTPUT")).append(";");
                    nlIndent();
                    this.sb.append("IRrecv _irrecv_" + blockName + "(_RECV_PIN_" + blockName + ");");
                    nlIndent();
                    this.sb.append("decode_results _results_" + blockName + ";");
                    nlIndent();
                    break;
                case SC.LIGHT:
                    this.sb.append("int _output_" + blockName + " = ").append(cc.getProperty("OUTPUT")).append(";");
                    nlIndent();
                    break;
                case SC.MOTION:
                    this.sb.append("int _output_" + blockName + " = ").append(cc.getProperty("OUTPUT")).append(";");
                    nlIndent();
                    break;
                case SC.POTENTIOMETER:
                    this.sb.append("int _output_" + blockName + " = ").append(cc.getProperty("OUTPUT")).append(";");
                    nlIndent();
                    break;
                case SC.TEMPERATURE:
                    this.sb.append("int _TMP36_" + blockName + " = ").append(cc.getProperty("OUTPUT")).append(";");
                    nlIndent();
                    break;
                // TODO check if there is any block for "ENCODER" implemented!
                case SC.ENCODER:
                    this.sb.append("int _CLK_" + blockName + " = 6;");
                    nlIndent();
                    this.sb.append("int _DT_" + blockName + " = 5;");
                    nlIndent();
                    this.sb.append("int _SW_" + blockName + " = ").append(cc.getProperty("OUTPUT")).append(";");
                    nlIndent();
                    this.sb.append("Encoder _myEncoder_" + blockName + "(_DT_" + blockName + ", _CLK_" + blockName + ");");
                    nlIndent();
                    break;
                case SC.DROP:
                    this.sb.append("int _S_" + blockName + " = ").append(cc.getProperty("S")).append(";");
                    nlIndent();
                    break;
                case SC.PULSE:
                    this.sb.append("int _SensorPin_" + blockName + " = ").append(cc.getProperty("S")).append(";");
                    nlIndent();
                    break;
                case SC.RFID:
                    this.sb.append("#define SS_PIN_" + blockName + " " + cc.getProperty("RST"));
                    nlIndent();
                    this.sb.append("#define RST_PIN_" + blockName + " " + cc.getProperty("SDA"));
                    nlIndent();
                    this.sb.append("MFRC522 _mfrc522_" + blockName + "(SS_PIN_" + blockName + ", RST_PIN_" + blockName + ");");
                    nlIndent();
                    break;
                case SC.KEY:
                    this.sb.append("int _taster_" + blockName + " = ").append(cc.getProperty("PIN1")).append(";");
                    nlIndent();
                    break;
                case SC.LCD:
                    this.sb
                        .append("LiquidCrystal _lcd_" + blockName + "(")
                        .append(cc.getProperty("RS"))
                        .append(", ")
                        .append(cc.getProperty("E"))
                        .append(", ")
                        .append(cc.getProperty("D4"))
                        .append(", ")
                        .append(cc.getProperty("D5"))
                        .append(", ")
                        .append(cc.getProperty("D6"))
                        .append(", ")
                        .append(cc.getProperty("D7"))
                        .append(");");
                    nlIndent();
                    break;
                case SC.LCDI2C:
                    this.sb.append("LiquidCrystal_I2C _lcd_" + blockName + "(0x27, 16, 2);");
                    nlIndent();
                    break;
                case SC.LED:
                    this.sb.append("int _led_" + blockName + " = ").append(cc.getProperty("INPUT")).append(";");
                    nlIndent();
                    break;
                case SC.RGBLED:
                    this.sb.append("int _led_red_" + blockName + " = ").append(cc.getProperty("RED")).append(";");
                    nlIndent();
                    this.sb.append("int _led_green_" + blockName + " = ").append(cc.getProperty("GREEN")).append(";");
                    nlIndent();
                    this.sb.append("int _led_blue_" + blockName + " = ").append(cc.getProperty("BLUE")).append(";");
                    nlIndent();
                    break;
                case SC.BUZZER:
                    this.sb.append("int _spiele_" + blockName + " = ").append(cc.getProperty("+")).append(";");
                    nlIndent();
                    break;
                case SC.RELAY:
                    this.sb.append("int _relay_" + blockName + " = ").append(cc.getProperty("IN")).append(";");
                    nlIndent();
                    break;
                case SC.STEPMOTOR:
                    this.sb.append("int _SPU_" + blockName + " = ").append("2048;"); //TODO: change 2048 to customized
                    nlIndent();
                    this.sb
                        .append("Stepper Motor_" + blockName + "(_SPU_" + blockName + ", ")
                        .append(cc.getProperty("IN1"))
                        .append(", ")
                        .append(cc.getProperty("IN2"))
                        .append(", ")
                        .append(cc.getProperty("IN3"))
                        .append(", ")
                        .append(cc.getProperty("IN4"))
                        .append(");");
                    nlIndent();
                    break;
                case SC.SERVOMOTOR:
                    this.sb.append("Servo _servo_" + blockName + ";");
                    nlIndent();
                    break;
                default:
                    throw new DbcException("Configuration block is not supported: " + cc.getComponentType());
            }
        }
    }

    @Override
    public Void visitPinWriteValueAction(PinWriteValueAction<Void> pinWriteValueSensor) {
        this.sb.append("pinMode(" + pinWriteValueSensor.getPort() + ", OUTPUT);");
        nlIndent();
        switch ( pinWriteValueSensor.getMode() ) {
            case SC.ANALOG:
                this.sb.append("analogWrite(" + pinWriteValueSensor.getPort() + ", ");
                pinWriteValueSensor.getValue().visit(this);
                this.sb.append(");");
                break;
            case SC.DIGITAL:
                this.sb.append("digitalWrite(" + pinWriteValueSensor.getPort() + ", ");
                pinWriteValueSensor.getValue().visit(this);
                this.sb.append(");");
                break;
            default:
                break;
        }
        return null;
    }

    @Override
    public Void visitSerialWriteAction(SerialWriteAction<Void> serialWriteAction) {
        this.sb.append("Serial.println(");
        serialWriteAction.getValue().visit(this);
        this.sb.append(");");
        return null;
    }
}
