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

	inputCols := []opt.ColumnID{
		e.f.Metadata().AddColumn("input_a", types.Int),
		e.f.Metadata().AddColumn("input_b", types.Int),
	}
	input := e.intValues(inputCols, []int{1, 2})
	projectCols := []opt.ColumnID{
		inputCols[0],
		e.f.Metadata().AddColumn("project_b", types.Int),
	}
	before := e.f.ConstructProject(input, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.num(3), projectCols[1]),
	}, opt.MakeColSet(inputCols[0]))
	want := e.intValues(projectCols, []int{1, 3})
	e.expectRewrite(rule, before, want)

	nearMiss := e.intValues(inputCols, []int{1, 7})
	nearMiss = e.f.ConstructProject(nearMiss, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.num(3), projectCols[1]),
	}, opt.MakeColSet(inputCols[0]))
	e.expectNoMatch(rule, nearMiss)
}
