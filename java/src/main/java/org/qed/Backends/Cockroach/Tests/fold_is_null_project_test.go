package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldIsNullProject(t *testing.T) {
	rule := loadRule(t, "FoldIsNullProject")
	e := newEnv(t)
	s, cols := e.scan("u", 2)
	z := e.f.Metadata().AddColumn("z", types.Bool)
	before := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructIs(e.v(cols[0]), memo.NullSingleton), z),
	}, opt.MakeColSet(cols[1]))
	want := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructFalse(), z),
	}, opt.MakeColSet(cols[1]))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructIs(e.v(cols[1]), memo.NullSingleton), z),
	}, opt.MakeColSet(cols[1])))
	e.expectNoMatch(rule, e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructIsNot(e.v(cols[0]), memo.NullSingleton), z),
	}, opt.MakeColSet(cols[1])))
}

func TestSQLFoldIsNullProject(t *testing.T) {
	expectSQLFires(t, "FoldIsNullProject", "SELECT a IS NULL AS z, b FROM (VALUES (1, 2), (3, 4)) AS v(a, b)")
}
