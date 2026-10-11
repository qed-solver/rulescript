package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestInlineProjectConstants(t *testing.T) {
	rule := loadRule(t, "InlineProjectConstants")
	e := newEnv(t)
	l, lc := e.scan("t", 1)
	r, rc := e.scan("u", 1)
	join := e.f.ConstructInnerJoin(l, r, e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))), &memo.JoinPrivate{})
	result := e.f.Metadata().AddColumn("result", types.Int)
	before := e.f.ConstructProject(join, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(lc[0]), e.num(1)), result),
	}, opt.ColSet{})
	want := e.f.ConstructProject(join, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(rc[0]), e.num(1)), result),
	}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructProject(join, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(rc[0]), e.num(1)), result),
	}, opt.ColSet{}))
	wrongJoin := e.f.ConstructInnerJoin(l, r, e.filters(e.f.ConstructNe(e.v(lc[0]), e.v(rc[0]))), &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructProject(wrongJoin, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(lc[0]), e.num(1)), result),
	}, opt.ColSet{}))
}

func TestSQLInlineProjectConstants(t *testing.T) {
	expectSQLRewrites(t, "InlineProjectConstants", "SELECT l.a + 1 FROM (VALUES (1), (2)) AS l(a) JOIN (VALUES (1)) AS r(b) ON l.a = r.b")
}
