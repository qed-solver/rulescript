package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestHoistSelectExists(t *testing.T) {
	rule := loadRule(t, "HoistSelectExists")
	e := newEnv(t)
	l, lc := e.scan("t", 3)
	s, sc := e.scan("u", 2)
	subSel := e.f.ConstructSelect(s, e.filters(e.f.ConstructGt(e.v(sc[0]), e.num(0))))
	rest := e.f.ConstructEq(e.v(lc[0]), e.num(1))
	before := e.f.ConstructSelect(l, e.filters(e.exists(subSel), rest))
	want := e.f.ConstructSelect(
		e.f.ConstructSemiJoin(l, subSel, e.filters(), &memo.JoinPrivate{}),
		e.filters(rest),
	)
	e.expectRewrite(rule, before, want)

	corrSel := e.f.ConstructSelect(s, e.filters(e.f.ConstructGt(e.v(sc[0]), e.v(lc[1]))))
	e.expectNoMatch(rule, e.f.ConstructSelect(l, e.filters(e.exists(corrSel), rest)))
	e.expectNoMatch(rule, e.f.ConstructSelect(l, e.filters(e.f.ConstructNot(e.exists(subSel)), rest)))
	e.expectNoMatch(rule, e.f.ConstructSelect(l, e.filters(e.exists(s), rest)))
	e.expectNoMatch(rule, e.f.ConstructSemiJoin(l, subSel, e.filters(), &memo.JoinPrivate{}))
}

func TestSQLHoistSelectExists(t *testing.T) {
	expectSQLFires(t, "HoistSelectExists", "SELECT a FROM t WHERE a = 1 AND EXISTS (SELECT x FROM u WHERE x > 0)")
}
