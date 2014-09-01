package de.fhg.iais.roberta.codegen.lejos;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.fhg.iais.roberta.ast.syntax.Phrase;
import de.fhg.iais.roberta.ast.syntax.expr.NumConst;
import de.fhg.iais.roberta.ast.syntax.expr.Var;
import de.fhg.iais.roberta.ast.visitor.AstDefaultVisitorInspecting;
import de.fhg.iais.roberta.ast.visitor.AstVisitor;
import de.fhg.iais.roberta.dbc.Assert;

/**
 * This class is implementing the {@link AstVisitor} interface.<br>
 * A list of all vars and numerical constants used in an AST are generated
 */
public class AstToVarsVisitor extends AstDefaultVisitorInspecting {
    private final Set<String> allVars = new HashSet<>();

    /**
     * initialize the Java code generator visitor.
     * 
     * @param programName name of the program
     * @param brickConfiguration hardware configuration of the brick
     * @param indentation to start with. Will be ince/decr depending on block structure
     */
    AstToVarsVisitor() {
    }

    /**
     * factory method to generate Java code from an AST.<br>
     * 
     * @param programName name of the program
     * @param brickConfiguration hardware configuration of the brick
     * @param phrases to generate the code from
     */
    public static Set<String> generate(List<Phrase<Void>> phrases) //
    {
        Assert.isTrue(phrases.size() >= 1);
        AstToVarsVisitor astVisitor = new AstToVarsVisitor();
        for ( Phrase<Void> phrase : phrases ) {
            phrase.visit(astVisitor);
        }
        return astVisitor.allVars;
    }

    @Override
    public Void visitVar(Var<Void> var) {
        String varName = var.getValue();
        this.allVars.add(varName);
        return null;
    }

    @Override
    public Void visitNumConst(NumConst<Void> numConst) {
        String numName = numConst.getValue();
        this.allVars.add(numName);
        return null;
    }
}
