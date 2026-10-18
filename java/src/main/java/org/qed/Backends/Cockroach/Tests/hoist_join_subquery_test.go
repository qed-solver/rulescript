package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestHoistJoinSubquery(t *testing.T) {
	rule := loadRule(t, "HoistJoinSubquery")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	s, sc := e.scan("nn", 1)
	on := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
	sub := e.f.ConstructSelect(s, e.filters(e.f.ConstructGt(e.v(sc[0]), e.num(0))))

	before := e.f.ConstructInnerJoin(l, r, e.filters(on, e.exists(sub)), &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(
		l,
		e.f.ConstructSelect(r, e.filters(e.exists(sub))),
		e.filters(on),
		&memo.JoinPrivate{},
	)
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, r, e.filters(on, e.exists(sub)), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, e.filters(e.f.ConstructNot(e.exists(sub))), &memo.JoinPrivate{}))

	correlated := e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(e.v(sc[0]), e.v(lc[0]))))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, e.filters(on, e.exists(correlated)), &memo.JoinPrivate{}))
}
