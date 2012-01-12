package de.unihd.dbs.uima.annotator.heideltime.substitutions;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.MatchResult;

public class Concatenation implements Expression {
	
	List<Expression> constituents;
	
	public Concatenation() {
		this.constituents = new ArrayList<Expression>();
	}
	
	public Concatenation(List<Expression> constituents) {
		this.constituents = constituents;
	}
	
	/**
	 * @return This concatenation's only element, or a flattened copy
	 */
	public Expression simplify() {
		if (constituents.size() == 1) {
			return constituents.get(0);
		}
		Concatenation res = new Concatenation();
		for (Expression cur : constituents) {
			if (cur instanceof Concatenation) {
				cur = ((Concatenation) cur).simplify();
			}
			if (cur instanceof Concatenation) {
				// Replace element by its constituents
				for (Expression toAdd : ((Concatenation) cur).constituents) {
					res.append(toAdd);
				}
			} else {
				res.append(cur);
			}
		}
		return res;
	}
	
	public void append(Expression exp) {
		this.constituents.add(exp);
	}

	@Override
	public CharSequence evaluate(MatchResult ruleMatch) {
		StringBuilder b = new StringBuilder();
		for (Expression exp : constituents) {
			b.append(exp.evaluate(ruleMatch));
		}
		return b;
	}
	
	public String toString() {
		StringBuilder res = new StringBuilder();
		for (Expression exp: constituents) {
			res.append(exp);
		}
		return res.toString();
	}
}
