package de.fhg.iais.roberta.ast.syntax.actors;

import org.junit.Test;

import de.fhg.iais.roberta.NxtAstTest;
import de.fhg.iais.roberta.util.test.UnitTestHelper;

public class MotorOnActionTest extends NxtAstTest {

    @Test
    public void motorOn() throws Exception {
        String a =
            "#defineWHEELDIAMETER5.6#defineTRACKWIDTH11.0#defineMAXLINES8#include\"NEPODefs.h\"//containsNEPOdeclarationsfortheNXCNXTAPIresources"
                + "OnFwdRegEx(OUT_B,SpeedTest(30),OUT_REGMODE_SPEED,RESET_NONE);OnFwdRegEx(OUT_C, SpeedTest(50), OUT_REGMODE_SPEED,RESET_NONE);}";

        UnitTestHelper
            .checkGeneratedSourceEqualityWithProgramXmlAndSourceAsString(testFactory, a, "/ast/actions/action_MotorOn.xml", brickConfigurationBC, true);
    }

    @Test
    public void motorOnFor() throws Exception {
        String a = "RotateMotor(OUT_B,SpeedTest(30), 360 * 1);";

        UnitTestHelper
            .checkGeneratedSourceEqualityWithProgramXmlAndSourceAsString(testFactory, a, "/ast/actions/action_MotorOnFor.xml", brickConfiguration, false);
    }
}