package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPruneValuesCols(t *testing.T) {
	rule := loadRule(t, "PruneValuesCols")
	e := newEnv(t)
	a := e.f.Metadata().AddColumn("a", types.Int)
	b := e.f.Metadata().AddColumn("b", types.Int)
	c := e.f.Metadata().AddColumn("c", types.Int)
	z := e.f.Metadata().AddColumn("z", types.Int)
	project := func(input memo.RelExpr, use opt.ColumnID) memo.RelExpr {
		return e.f.ConstructProject(input, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(use), e.num(5)), z),
		}, opt.MakeColSet(b))
	}
	before := project(e.intValues([]opt.ColumnID{a, b, c}, []int{1, 2, 3}), a)
	want := project(e.intValues([]opt.ColumnID{a, b}, []int{1, 2}), a)
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, project(e.intValues([]opt.ColumnID{a, b, c}, []int{1, 2, 4}), a))
	e.expectNoMatch(rule, project(e.intValues([]opt.ColumnID{a, b, c}, []int{1, 2, 3}), c))
}

func TestSQLPruneValuesCols(t *testing.T) {
	expectSQLFires(t, "PruneValuesCols", "SELECT a+5 AS z,b FROM (VALUES (1,2,3)) AS v(a,b,c)")
}
