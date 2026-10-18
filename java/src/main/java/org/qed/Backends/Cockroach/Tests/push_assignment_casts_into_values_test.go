package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestPushAssignmentCastsIntoValues(t *testing.T) {
	rule := loadRule(t, "PushAssignmentCastsIntoValues")
	t.Run("match", func(t *testing.T) {
		e := newEnv(t)
		input, cols := e.scan("t", 1)
		castCol := e.f.Metadata().AddColumn("cast", e.v(cols[0]).DataType())
		before := e.f.ConstructProject(input, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.v(cols[0]), castCol),
			e.f.ConstructProjectionsItem(memo.TrueSingleton, e.f.Metadata().AddColumn("other", e.v(cols[0]).DataType())),
		}, opt.ColSet{})
		pushed := e.f.ConstructProject(input, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.v(cols[0]), castCol),
		}, opt.ColSet{})
		want := e.f.ConstructProject(pushed, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(memo.TrueSingleton, e.f.Metadata().AddColumn("other", e.v(cols[0]).DataType())),
		}, opt.MakeColSet(castCol))
		e.expectRewriteUpToFreshColumns(rule, before, want)
	})
	t.Run("near-miss", func(t *testing.T) {
		e := newEnv(t)
		input, cols := e.scan("t", 1)
		castCol := e.f.Metadata().AddColumn("cast", e.v(cols[0]).DataType())
		plan := e.f.ConstructProject(input, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.v(cols[0]), castCol),
		}, opt.ColSet{})
		e.expectNoMatch(rule, plan)
	})
}
