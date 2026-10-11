package rulescript_test

import (
	"testing"
)

func TestFoldComparison(t *testing.T) {
	rule := loadRule(t, "FoldComparison")
	e := newEnv(t)
	s, _ := e.scan("u", 2)
	before := e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(e.f.ConstructTrue(), e.f.ConstructFalse())))
	want := e.f.ConstructSelect(s, e.filters(e.f.ConstructFalse()))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(e.f.ConstructFalse(), e.f.ConstructTrue()))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(e.f.ConstructTrue(), e.f.ConstructTrue()))))
}

func TestSQLFoldComparison(t *testing.T) {
	expectSQLRewrites(t, "FoldComparison", "SELECT x FROM u WHERE TRUE = FALSE")
}
