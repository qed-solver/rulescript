package rulescript_test

import (
	"testing"
)

func TestFoldComparisonWithAny(t *testing.T) {
	rule := loadRule(t, "FoldComparisonWithAny")
	e := newEnv(t)
	s, _ := e.scan("u", 2)
	falseExpr := e.f.ConstructEq(e.f.ConstructTrue(), e.f.ConstructFalse())
	trueExpr := e.f.ConstructEq(e.f.ConstructTrue(), e.f.ConstructTrue())
	before := e.f.ConstructSelect(s, e.filters(e.f.ConstructOr(falseExpr, trueExpr)))
	e.expectRewrite(rule, before, s)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructOr(trueExpr, falseExpr))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructOr(falseExpr, falseExpr))))
}

func TestSQLFoldComparisonWithAny(t *testing.T) {
	expectSQLRewrites(t, "FoldComparisonWithAny", "SELECT x FROM u WHERE (TRUE = FALSE) OR (TRUE = TRUE)")
}
