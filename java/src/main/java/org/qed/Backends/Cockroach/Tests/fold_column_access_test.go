package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldColumnAccess(t *testing.T) {
	rule := loadRule(t, "FoldColumnAccess")
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	e0 := e.f.Metadata().AddColumn("e0", types.Int)
	e1 := e.f.Metadata().AddColumn("e1", types.Int)
	second := e.f.ConstructPlus(e.v(cols[0]), e.num(1))
	inner := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(cols[0]), e0),
		e.f.ConstructProjectionsItem(second, e1),
	}, opt.ColSet{})
	before := e.f.ConstructProject(inner, nil, opt.MakeColSet(e1))
	want := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.num(1)), e1),
	}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructProject(inner, nil, opt.MakeColSet(e0)))
	e.expectNoMatch(rule, e.f.ConstructProject(inner, nil, opt.MakeColSet(e0, e1)))
	e.expectNoMatch(rule, inner)
}

func TestSQLFoldColumnAccess(t *testing.T) {
	expectSQLFiresOrNot(t, "FoldColumnAccess", "SELECT b FROM (SELECT a+1 AS a, a+2 AS b FROM (VALUES (1),(2)) AS v(a)) AS q", true)
}
