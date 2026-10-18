package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestHoistSelectNotExists(t *testing.T) {
	rule := loadRule(t, "HoistSelectNotExists")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	s, sc := e.scan("t", 1)
	subFilter := e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(e.v(sc[0]), e.num(1))))
	rest := e.f.ConstructEq(e.v(lc[0]), e.num(2))
	before := e.f.ConstructSelect(l, e.filters(e.f.ConstructNot(e.exists(subFilter)), rest))
	want := e.f.ConstructSelect(
		e.f.ConstructAntiJoin(l, subFilter, e.filters(), &memo.JoinPrivate{}),
		e.filters(rest),
	)
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(l, e.filters(e.exists(subFilter), rest)))
	s2, sc2 := e.scan("t", 1)
	correlated := e.f.ConstructSelect(s2, e.filters(e.f.ConstructEq(e.v(sc2[0]), e.v(lc[0]))))
	e.expectNoMatch(rule, e.f.ConstructSelect(l, e.filters(e.f.ConstructNot(e.exists(correlated)), rest)))
}
