package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestJoinDeriveIsNotNullFilter(t *testing.T) {
	rule := loadRule(t, "JoinDeriveIsNotNullFilter")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	eq := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
	gt := e.f.ConstructGt(e.v(lc[0]), e.v(rc[0]))
	on := e.filters(eq, gt)
	before := e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(
		e.f.ConstructSelect(l, e.filters(e.f.ConstructIsNot(e.v(lc[0]), memo.NullSingleton))),
		e.f.ConstructSelect(r, e.filters(e.f.ConstructIsNot(e.v(rc[0]), memo.NullSingleton))),
		on,
		&memo.JoinPrivate{},
	)
	e.expectRewrite(rule, before, want)

	noEquality := e.filters(e.f.ConstructGt(e.v(lc[0]), e.v(rc[0])))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, noEquality, &memo.JoinPrivate{}))
}
