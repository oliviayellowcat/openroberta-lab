package de.fhg.iais.roberta.visitor.codegen;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import de.fhg.iais.roberta.codegen.HelperMethodGenerator;
import de.fhg.iais.roberta.components.Category;
import de.fhg.iais.roberta.components.Configuration;
import de.fhg.iais.roberta.components.ConfigurationComponent;
import de.fhg.iais.roberta.inter.mode.action.ILanguage;
import de.fhg.iais.roberta.syntax.BlockType;
import de.fhg.iais.roberta.syntax.BlockTypeContainer;
import de.fhg.iais.roberta.syntax.Phrase;
import de.fhg.iais.roberta.syntax.SCRaspberryPi;
import de.fhg.iais.roberta.syntax.action.light.LightAction;
import de.fhg.iais.roberta.syntax.action.light.LightStatusAction;
import de.fhg.iais.roberta.syntax.action.raspberrypi.LedBlinkAction;
import de.fhg.iais.roberta.syntax.action.raspberrypi.LedDimAction;
import de.fhg.iais.roberta.syntax.action.raspberrypi.LedGetAction;
import de.fhg.iais.roberta.syntax.action.raspberrypi.LedSetAction;
import de.fhg.iais.roberta.syntax.action.speech.SayTextAction;
import de.fhg.iais.roberta.syntax.action.speech.SetLanguageAction;
import de.fhg.iais.roberta.syntax.lang.blocksequence.MainTask;
import de.fhg.iais.roberta.syntax.lang.expr.ColorHexString;
import de.fhg.iais.roberta.syntax.lang.expr.ConnectConst;
import de.fhg.iais.roberta.syntax.lang.expr.Expr;
import de.fhg.iais.roberta.syntax.lang.stmt.IfStmt;
import de.fhg.iais.roberta.syntax.lang.stmt.IntentStmt;
import de.fhg.iais.roberta.syntax.lang.stmt.StmtList;
import de.fhg.iais.roberta.syntax.lang.stmt.WaitStmt;
import de.fhg.iais.roberta.syntax.lang.stmt.WaitTimeStmt;
import de.fhg.iais.roberta.syntax.sensor.generic.KeysSensor;
import de.fhg.iais.roberta.syntax.sensor.generic.TimerSensor;
import de.fhg.iais.roberta.syntax.sensor.generic.UltrasonicSensor;
import de.fhg.iais.roberta.syntax.sensors.raspberrypi.SlotSensor;
import de.fhg.iais.roberta.util.dbc.Assert;
import de.fhg.iais.roberta.util.dbc.DbcException;
import de.fhg.iais.roberta.visitor.IVisitor;
import de.fhg.iais.roberta.visitor.collect.RaspberryPiUsedHardwareCollectorVisitor;
import de.fhg.iais.roberta.visitor.collect.RaspberryPiUsedMethodCollectorVisitor;
import de.fhg.iais.roberta.visitor.hardware.IRaspberryPiVisitor;
import de.fhg.iais.roberta.visitor.lang.codegen.prog.AbstractPythonVisitor;

/**
 * This class is implementing {@link IVisitor}. All methods are implemented and they append a human-readable Python code representation of a phrase to a
 * StringBuilder. <b>This representation is correct Python code.</b> <br>
 */
public final class RaspberryPiPythonVisitor extends AbstractPythonVisitor implements IRaspberryPiVisitor<Void> {
    protected final Configuration brickConfiguration;
    private final RaspberryPiUsedHardwareCollectorVisitor usedHardwareCollector;

    /**
     * initialize the Python code generator visitor.
     *
     * @param brickConfiguration hardware configuration of the brick
     * @param programPhrases to generate the code from
     * @param indentation to start with. Will be ince/decr depending on block structure
     */
    private RaspberryPiPythonVisitor(
        Configuration brickConfiguration,
        ArrayList<ArrayList<Phrase<Void>>> programPhrases,
        int indentation,
        ILanguage language,
        HelperMethodGenerator helperMethodGenerator) {
        super(programPhrases, indentation, helperMethodGenerator, new RaspberryPiUsedMethodCollectorVisitor(programPhrases));
        this.usedHardwareCollector = new RaspberryPiUsedHardwareCollectorVisitor(programPhrases, brickConfiguration);
        this.brickConfiguration = brickConfiguration;
        this.usedGlobalVarInFunctions = this.usedHardwareCollector.getMarkedVariablesAsGlobal();
        this.loopsLabels = this.usedHardwareCollector.getloopsLabelContainer();
    }

    /**
     * factory method to generate Python code from an AST.<br>
     *
     * @param brickConfiguration hardware configuration of the brick
     * @param programPhrases to generate the code from
     */
    public static String generate(
        Configuration brickConfiguration,
        ArrayList<ArrayList<Phrase<Void>>> programPhrases,
        boolean withWrapping,
        ILanguage language,
        HelperMethodGenerator helperMethodGenerator) {
        Assert.notNull(brickConfiguration);

        RaspberryPiPythonVisitor astVisitor = new RaspberryPiPythonVisitor(brickConfiguration, programPhrases, 0, language, helperMethodGenerator);
        astVisitor.generateCode(withWrapping);

        return astVisitor.sb.toString();
    }

    @Override
    public Void visitColorHexString(ColorHexString<Void> colorHexString) {
        this.sb.append(quote(colorHexString.getValue()));
        return null;
    }

    @Override
    public Void visitWaitStmt(WaitStmt<Void> waitStmt) {
        this.sb.append("while True:");
        incrIndentation();
        visitStmtList(waitStmt.getStatements());
        nlIndent();
        this.sb.append("sleep(0.001)");
        decrIndentation();
        return null;
    }

    @Override
    public Void visitWaitTimeStmt(WaitTimeStmt<Void> waitTimeStmt) {
        this.sb.append("sleep(");
        waitTimeStmt.getTime().visit(this);
        this.sb.append(" / 1000)");
        return null;
    }

    @Override
    public Void visitTimerSensor(TimerSensor<Void> timerSensor) {
        switch ( timerSensor.getMode() ) {
            case SCRaspberryPi.INTENT:
            case SCRaspberryPi.DEFAULT:
            case SCRaspberryPi.VALUE:
                this.sb.append("hal.getTimerValue(1)");
                break;
            case SCRaspberryPi.RESET:
                this.sb.append("hal.resetTimer(1)");
                break;
            default:
                throw new DbcException("Invalid Time Mode!");
        }
        return null;
    }

    @Override
    public Void visitUltrasonicSensor(UltrasonicSensor<Void> ultrasonicSensor) {
        this.sb.append("hal.get_sensor_status()");
        return null;
    }

    @Override
    public Void visitMainTask(MainTask<Void> mainTask) {
        StmtList<Void> variables = mainTask.getVariables();
        variables.visit(this);
        nlIndent();
        this.sb.append("board = Board()");
        nlIndent();
        nlIndent();
        generateUserDefinedMethods();
        this.programPhrases.stream().filter(phrase -> phrase.getKind().getCategory() == Category.DIALOG).forEach(e -> {
            e.visit(this);
        });
        nlIndent();
        nlIndent();
        this.sb.append("def run():");
        incrIndentation();
        nlIndent();
        //this.usedGlobalVarInFunctions = this.usedHardwareCollector.getMarkedVariablesAsGlobal();        
        if ( !this.usedGlobalVarInFunctions.isEmpty() ) {
            nlIndent();
            this.sb.append("global " + String.join(", ", this.usedGlobalVarInFunctions));
        } else {
            addPassIfProgramIsEmpty();
        }
        nlIndent();
        return null;
    }

    @Override
    protected void generateCode(boolean withWrapping) {
        generateProgramPrefix(withWrapping);
        generateProgramMainBody();
        generateProgramSuffix(withWrapping);
    }

    private void generateProgramMainBody() {
        this.programPhrases
            .stream()
            .filter(
                phrase -> (phrase.getKind().getCategory() != Category.METHOD && phrase.getKind().getCategory() != Category.DIALOG)
                    || phrase.getKind().hasName("METHOD_CALL"))
            .forEach(p -> {
                nlIndent();
                p.visit(this);
            });
    }

    @Override
    public Void visitConnectConst(ConnectConst<Void> connectConst) {
        return null;
    }

    @Override
    protected void generateProgramPrefix(boolean withWrapping) {
        if ( !withWrapping ) {
            return;
        }
        this.sb.append("#!/usr/bin/python");
        nlIndent();
        nlIndent();
        this.sb.append("from aiy.board import Board, Led");
        nlIndent();
        this.sb.append("import math");
        nlIndent();
        this.sb.append("import random");
        nlIndent();
        this.sb.append("from time import sleep");
        nlIndent();
        this.sb.append("import speech_recognizer_roberta");
        nlIndent();
        this.sb.append("import os");
        nlIndent();
        nlIndent();
        this.sb.append("class BreakOutOfALoop(Exception): pass");
        nlIndent();
        this.sb.append("class ContinueLoop(Exception): pass");
        nlIndent();
        for ( ConfigurationComponent usedConfigurationBlock : this.brickConfiguration.getConfigurationComponentsValues() ) {
            switch ( usedConfigurationBlock.getComponentType() ) {
                case SCRaspberryPi.INTENT:
                case SCRaspberryPi.SLOT:
                    this.sb
                        .append(usedConfigurationBlock.getComponentType())
                        .append("_")
                        .append(usedConfigurationBlock.getUserDefinedPortName().toLowerCase())
                        .append(" = [\"");
                    this.sb
                        .append(
                            usedConfigurationBlock
                                .getComponentProperties()
                                .entrySet()
                                .stream()
                                .map(s -> s.getValue().toLowerCase())
                                .collect(Collectors.joining("\", \"")));
                    this.sb.append("\"]");
                    nlIndent();

                    break;
                case SCRaspberryPi.KEY:
                    break;
                case SCRaspberryPi.LED:
                    break;
                default:
                    throw new DbcException("Configuration block is not supported: " + usedConfigurationBlock.getComponentType());
            }
        }
    }

    @Override
    protected void generateProgramSuffix(boolean withWrapping) {
        if ( !withWrapping ) {
            return;
        }
        nlIndent();

        this.sb.append("while True:");
        incrIndentation();
        nlIndent();
        this.sb.append("myphrase = speech_recognizer_roberta.recognize_speech()");
        nlIndent();
        this.sb.append("print(myphrase)");
        nlIndent();
        if ( this.usedHardwareCollector.getIntents().isEmpty() ) {
            this.sb.append("if False:");
            incrIndentation();
            nlIndent();
            this.sb.append("pass");
            decrIndentation();
            nlIndent();
        } else {
            this.usedHardwareCollector.getIntents().stream().findFirst().ifPresent(i -> {
                this.sb.append("if intent_").append(i.toLowerCase()).append("(myphrase):");
                incrIndentation();
                nlIndent();
                this.sb.append("pass");
                decrIndentation();
                nlIndent();
            });
            this.usedHardwareCollector.getIntents().stream().skip(1).forEach(i -> {
                this.sb.append("elif intent_").append(i.toLowerCase()).append("(myphrase):");
                incrIndentation();
                nlIndent();
                this.sb.append("pass");
                decrIndentation();
                nlIndent();
            });
        }
        this.sb.append("else:");
        incrIndentation();
        nlIndent();
        String pleaseRepeat = "Ich habe das nicht verstanden, bitte versuchen Sie es noch einmal";
        this.sb.append("print(os.system('sh speech_syntheziser.sh \"").append(pleaseRepeat).append("\"'))");
        decrIndentation();
        nlIndent();
        decrIndentation();
        decrIndentation(); // everything is still indented from main program
        nlIndent();
        nlIndent();
        this.sb.append("def main():");
        incrIndentation();
        nlIndent();
        this.sb.append("try:");
        incrIndentation();
        nlIndent();
        this.sb.append("run()");
        decrIndentation();
        nlIndent();
        this.sb.append("except Exception as e:");
        incrIndentation();
        nlIndent();
        this.sb.append("print('Fehler!!!!')");
        nlIndent();
        this.sb.append("print(e.__class__.__name__)");
        nlIndent();
        // FIXME: we can only print about 30 chars
        this.sb.append("print(e)");
        decrIndentation();
        decrIndentation();
        nlIndent();
        nlIndent();
        if ( !this.usedHardwareCollector.getUsedMethods().isEmpty() ) {
            String helperMethodImpls = this.helperMethodGenerator.getHelperMethodDefinitions(this.usedHardwareCollector.getUsedMethods());
            this.sb.append(helperMethodImpls);
        }
        if ( !this.languageCollectorVisitor.getUsedFunctions().isEmpty() ) {
            String helperMethodImpls = this.helperMethodGenerator.getHelperMethodDefinitions(this.languageCollectorVisitor.getUsedFunctions());
            this.sb.append(helperMethodImpls);
        }
        nlIndent();
        this.sb.append("if __name__ == \"__main__\":");
        incrIndentation();
        nlIndent();
        this.sb.append("main()");

    }

    private String quote(String value) {
        return "'" + value.toLowerCase() + "'";
    }

    @Override
    public Void visitLightAction(LightAction<Void> lightAction) {
        this.sb.append("board.led.state = Led.").append(lightAction.getMode().toString());
        return null;
    }

    @Override
    public Void visitLightStatusAction(LightStatusAction<Void> lightStatusAction) {
        this.sb.append("hal.light_off(").append(lightStatusAction.getPort()).append(")");
        return null;
    }

    @Override
    public Void visitLedSetAction(LedSetAction<Void> ledSetAction) {
        this.sb.append("hal.set_brightness(").append(ledSetAction.getPort()).append(", ");
        ledSetAction.getBrightness().visit(this);
        this.sb.append(")");
        return null;
    }

    @Override
    public Void visitLedBlinkAction(LedBlinkAction<Void> ledBlinkAction) {
        this.sb.append("hal.blink(").append(ledBlinkAction.getPort()).append(", ");
        ledBlinkAction.getFrequency().visit(this);
        this.sb.append(", ");
        ledBlinkAction.getDuration().visit(this);
        this.sb.append(")");
        return null;
    }

    @Override
    public Void visitLedDimAction(LedDimAction<Void> ledDimAction) {
        this.sb.append("hal.dim(").append(ledDimAction.getPort()).append(", ");
        ledDimAction.getFrom().visit(this);
        this.sb.append(", ");
        ledDimAction.getTo().visit(this);
        this.sb.append(", ");
        ledDimAction.getDuration().visit(this);
        this.sb.append(")");
        return null;
    }

    @Override
    public Void visitLedGetAction(LedGetAction<Void> ledGetAction) {
        this.sb.append("hal.get_brightness(").append(ledGetAction.getPort()).append(")");
        return null;
    }

    @Override
    public Void visitSetLanguageAction(SetLanguageAction<Void> setLanguageAction) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Void visitSayTextAction(SayTextAction<Void> sayTextAction) {
        this.sb.append("print(os.system('sh speech_syntheziser.sh ");
        sayTextAction.getMsg().visit(this);
        this.sb.append("'))");
        return null;
    }

    @Override
    protected void generateUserDefinedMethods() {
        super.generateUserDefinedMethods();
        incrIndentation();
        decrIndentation();
    }

    @Override
    public Void visitIntentStmt(IntentStmt<Void> intentStmt) {
        nlIndent();
        nlIndent();
        String intentName = intentStmt.getIntent().toLowerCase();
        this.sb.append("def intent_").append(intentName).append("(phrase):");
        incrIndentation();
        nlIndent();
        if ( !this.usedGlobalVarInFunctions.isEmpty() ) {
            this.sb.append("global " + String.join(", ", this.usedGlobalVarInFunctions));
            nlIndent();
        }
        this.sb.append("if _contains(phrase.lower(), INTENT_").append(intentName.toLowerCase()).append("):");
        incrIndentation();
        if ( intentStmt.get_elseIf() > 0 ) {
            for ( int i = 0; i < intentStmt.getThenList().size(); i++ ) {
                if ( !intentStmt.getExpr().get(i).getKind().hasName("EMPTY_EXPR") ) {
                    nlIndent();
                    this.sb.append("if _contains(phrase.lower(), ");
                    intentStmt.getExpr().get(i).visit(this);
                    this.sb.append("):");
                    incrIndentation();
                    intentStmt.getThenList().get(i).visit(this);
                    nlIndent();
                    this.sb.append("return True");
                    decrIndentation();
                }
            }
            intentStmt.getElseList().visit(this);
            nlIndent();
            this.sb.append("return True");
        } else {
            if ( intentStmt.getThenList().get(0).get().size() != 0 ) {
                intentStmt.getThenList().get(0).visit(this);
            }
            if ( intentStmt.getIntent().toLowerCase().contentEquals("stop") ) {
                nlIndent();
                this.sb.append("os.exit(1)");
            } else {
                nlIndent();
                this.sb.append("return True");
            }
        }
        decrIndentation();
        nlIndent();
        this.sb.append("else:");
        incrIndentation();
        nlIndent();
        this.sb.append("return False");
        decrIndentation();
        decrIndentation();
        nlIndent();
        return null;
    }

    @Override
    public Void visitSlotSensor(SlotSensor<Void> slotSensor) {
        this.sb.append("SLOT_").append(slotSensor.getValue().toLowerCase());
        return null;
    }

    @Override
    public Void visitKeysSensor(KeysSensor<Void> keysSensor) {
        this.sb.append("_key_pressed()");
        return null;
    }
}
