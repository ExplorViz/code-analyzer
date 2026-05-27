package net.explorviz.code.analysis.antlr.generated.swift;

import java.util.Stack;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;

public abstract class SwiftSupportLexer extends Lexer
{
    protected SwiftSupportLexer(CharStream input)
    {
        super(input);
    }

	public Stack<Integer> parenthesis = new Stack<Integer>();

    @Override
	public void reset(){
		super.reset();
		parenthesis.clear();
	}
}
