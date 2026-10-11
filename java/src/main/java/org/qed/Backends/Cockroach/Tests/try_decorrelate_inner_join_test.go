package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestTryDecorrelateInnerJoin(t *testing.T) {
	rule := loadRule(t, "TryDecorrelateInnerJoin")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	il, ilc := e.scan("t", 1)
	ir, irc := e.scan("nn", 1)
	innerOn := e.filters(e.f.ConstructEq(e.v(ilc[0]), e.v(irc[0])))
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(ilc[0])))
	inner := e.f.ConstructInnerJoin(il, ir, innerOn, &memo.JoinPrivate{})
	before := e.f.ConstructInnerJoin(l, inner, on, &memo.JoinPrivate{})
	cross := e.f.ConstructInnerJoin(il, ir, nil, &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(l, cross, append(append(memo.FiltersExpr{}, on...), innerOn...), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, inner, on, &memo.JoinPrivate{}))
	wrongInner := e.f.ConstructLeftJoin(il, ir, innerOn, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, wrongInner, on, &memo.JoinPrivate{}))
}

func TestSQLTryDecorrelateInnerJoin(t *testing.T) {
	expectSQLFires(t, "TryDecorrelateInnerJoin", "SELECT * FROM (VALUES (1)) AS l(a) JOIN ((VALUES (1)) AS i(b) JOIN (VALUES (1)) AS r(c) ON b=c) ON a=b")
}
