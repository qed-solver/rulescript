package rulescript_test

import (
	"testing"
)

func TestFoldBinary(t *testing.T) {
	rule := loadRule(t, "FoldBinary")
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	before := e.f.ConstructSelect(s, e.filters(e.f.ConstructAnd(e.f.ConstructTrue(), e.f.ConstructFalse())))
	want := e.f.ConstructSelect(s, e.filters(e.f.ConstructFalse()))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructAnd(e.f.ConstructFalse(), e.f.ConstructTrue()))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructAnd(e.f.ConstructTrue(), e.f.ConstructGt(e.v(cols[0]), e.num(0))))))
}

func TestSQLFoldBinary(t *testing.T) {
	expectSQLFires(t, "FoldBinary", "SELECT x FROM u WHERE TRUE AND FALSE")
}
