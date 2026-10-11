package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPruneProjectCols(t *testing.T) {
	rule := loadRule(t, "PruneProjectCols")
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	used := e.f.Metadata().AddColumn("used", types.Int)
	unused := e.f.Metadata().AddColumn("unused", types.Int)
	out := e.f.Metadata().AddColumn("out", types.Int)
	inner := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.num(1)), used),
		e.f.ConstructProjectionsItem(e.f.ConstructMult(e.v(cols[0]), e.num(2)), unused),
	}, opt.MakeColSet(cols[0]))
	before := e.f.ConstructProject(inner, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructMult(e.v(cols[0]), e.v(used)), out),
	}, opt.ColSet{})
	want := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructMult(e.v(cols[0]), e.f.ConstructPlus(e.v(cols[0]), e.num(1))), out),
	}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructProject(inner, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructMult(e.v(cols[0]), e.v(unused)), out),
	}, opt.ColSet{}))
	e.expectNoMatch(rule, e.f.ConstructProject(inner, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructMult(e.v(cols[0]), e.v(used)), out),
	}, opt.MakeColSet(used)))
	e.expectNoMatch(rule, inner)
}

func TestSQLPruneProjectCols(t *testing.T) {
	expectSQLFiresOrNot(t, "PruneProjectCols", "SELECT a*b FROM (SELECT a,a+1 AS b,a+2 AS unused FROM (VALUES (1),(2)) AS v(a)) AS q", true)
}
