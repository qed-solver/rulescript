package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestTryDecorrelateInnerJoin(t *testing.T) {
	rule := loadRule(t, "TryDecorrelateInnerJoin")
	e := newEnv(t)

	l, lc := e.scan("t", 1)
	il, ilc := e.scan("t", 1)
	ir, irc := e.scan("t", 1)
	innerCond := e.filters(e.f.ConstructEq(e.v(ilc[0]), e.v(irc[0])))
	inner := e.f.ConstructInnerJoin(il, ir, innerCond, &memo.JoinPrivate{})
	before := e.f.ConstructInnerJoin(l, inner, e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(irc[0]))), &memo.JoinPrivate{})

	wantInner := e.f.ConstructInnerJoin(il, ir, nil, &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(l, wantInner, e.filters(
		e.f.ConstructEq(e.v(lc[0]), e.v(irc[0])),
		e.f.ConstructEq(e.v(ilc[0]), e.v(irc[0])),
	), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	nearMiss := e.f.ConstructLeftJoin(l, inner, e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(irc[0]))), &memo.JoinPrivate{})
	e.expectNoMatch(rule, nearMiss)
}
