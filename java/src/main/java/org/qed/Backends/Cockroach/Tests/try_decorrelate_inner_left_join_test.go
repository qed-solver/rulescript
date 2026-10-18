package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestTryDecorrelateInnerLeftJoin(t *testing.T) {
	rule := loadRule(t, "TryDecorrelateInnerLeftJoin")
	e := newEnv(t)
	l, lc := e.scan("t", 1)
	il, ilc := e.scan("t", 1)
	ir, irc := e.scan("t", 1)
	inner := e.f.ConstructLeftJoin(il, ir, e.filters(e.f.ConstructEq(e.v(ilc[0]), e.v(irc[0]))), &memo.JoinPrivate{})
	before := e.f.ConstructInnerJoin(l, inner, e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(ilc[0]))), &memo.JoinPrivate{})
	wantLeft := e.f.ConstructInnerJoin(l, il, e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(ilc[0]))), &memo.JoinPrivate{})
	want := e.f.ConstructLeftJoin(wantLeft, ir, e.filters(e.f.ConstructEq(e.v(ilc[0]), e.v(irc[0]))), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	nl, nlc := e.scan("t", 1)
	nilLeft, nilc := e.scan("t", 1)
	nir, nirc := e.scan("t", 1)
	nearInner := e.f.ConstructInnerJoin(nilLeft, nir, e.filters(e.f.ConstructEq(e.v(nilc[0]), e.v(nirc[0]))), &memo.JoinPrivate{})
	nearMiss := e.f.ConstructInnerJoin(nl, nearInner, e.filters(e.f.ConstructEq(e.v(nlc[0]), e.num(1))), &memo.JoinPrivate{})
	e.expectNoMatch(rule, nearMiss)
}
