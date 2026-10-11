package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestTryDecorrelateProjectInnerJoin(t *testing.T) {
	rule := loadRule(t, "TryDecorrelateProjectInnerJoin")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	il, ilc := e.scan("t", 1)
	ir, irc := e.scan("nn", 1)
	z := e.f.Metadata().AddColumn("z", types.Int)
	computed := e.f.ConstructPlus(e.v(ilc[0]), e.v(irc[0]))
	innerOn := e.filters(e.f.ConstructEq(e.v(ilc[0]), e.v(irc[0])))
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(z)))
	inner := e.f.ConstructInnerJoin(il, ir, innerOn, &memo.JoinPrivate{})
	beforeRight := e.f.ConstructProject(inner, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(computed, z),
	}, opt.MakeColSet(ilc[0]))
	before := e.f.ConstructLeftJoin(l, beforeRight, on, &memo.JoinPrivate{})
	cross := e.f.ConstructInnerJoin(il, ir, nil, &memo.JoinPrivate{})
	afterRight := e.f.ConstructProject(cross, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(computed, z),
	}, opt.MakeColSet(ilc[0], irc[0]))
	afterJoin := e.f.ConstructLeftJoin(l, afterRight,
		append(append(memo.FiltersExpr{}, on...), innerOn...), &memo.JoinPrivate{})
	want := e.f.ConstructProject(afterJoin, nil, opt.MakeColSet(lc[0], ilc[0], z))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, beforeRight, on, &memo.JoinPrivate{}))
	wrongInner := e.f.ConstructLeftJoin(il, ir, innerOn, &memo.JoinPrivate{})
	wrongRight := e.f.ConstructProject(wrongInner, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(computed, z),
	}, opt.MakeColSet(ilc[0]))
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, wrongRight, on, &memo.JoinPrivate{}))
}

func TestSQLTryDecorrelateProjectInnerJoin(t *testing.T) {
	expectSQLFires(t, "TryDecorrelateProjectInnerJoin", "SELECT * FROM (VALUES (1)) AS l(a) LEFT JOIN (SELECT b,b+c AS z FROM (VALUES (1)) AS i(b) JOIN (VALUES (1)) AS r(c) ON b=c) AS q ON a=z")
}
