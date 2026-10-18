package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestDecorrelateProjectSet(t *testing.T) {
	rule := loadRule(t, "DecorrelateProjectSet")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	on := e.filters(e.f.ConstructEq(e.v(rc[0]), e.num(5)))
	before := e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(l, e.f.ConstructSelect(r, on), nil, &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	onLeft := e.filters(e.f.ConstructEq(e.v(lc[0]), e.num(5)))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, onLeft, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{}))

	wide, wc := e.scan("t", 2)
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, wide, e.filters(e.f.ConstructEq(e.v(wc[0]), e.num(5))), &memo.JoinPrivate{}))
}
