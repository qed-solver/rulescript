package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestMergeProjectWithValues(t *testing.T) {
	rule := loadRule(t, "MergeProjectWithValues")
	e := newEnv(t)
	a := e.f.Metadata().AddColumn("a", types.Int)
	b := e.f.Metadata().AddColumn("b", types.Int)
	c := e.f.Metadata().AddColumn("c", types.Int)
	values := e.intValues([]opt.ColumnID{a, b}, []int{1, 2})
	project := func(n int, pass opt.ColumnID) memo.RelExpr {
		return e.f.ConstructProject(values, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.num(n), c),
		}, opt.MakeColSet(pass))
	}
	e.expectRewrite(rule, project(3, a), e.intValues([]opt.ColumnID{a, c}, []int{1, 3}))
	e.expectNoMatch(rule, project(4, a))
	e.expectNoMatch(rule, project(3, b))
}

func TestSQLMergeProjectWithValues(t *testing.T) {
	expectSQLFires(t, "MergeProjectWithValues", "SELECT a,3 AS c FROM (VALUES (1,2)) AS v(a,b)")
}
