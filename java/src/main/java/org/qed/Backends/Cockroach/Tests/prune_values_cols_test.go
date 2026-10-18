package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPruneValuesCols(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "PruneValuesCols")
	cols := []opt.ColumnID{
		e.f.Metadata().AddColumn("a", types.Int),
		e.f.Metadata().AddColumn("b", types.Int),
		e.f.Metadata().AddColumn("c", types.Int),
	}
	projected := e.f.Metadata().AddColumn("p", types.Int)
	input := e.intValues(cols, []int{1, 2, 3})
	before := e.f.ConstructProject(input, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(cols[0]), projected),
	}, opt.MakeColSet(cols[1]))
	wantValues := e.intValues([]opt.ColumnID{cols[0], cols[1]}, []int{1, 2})
	want := e.f.ConstructProject(wantValues, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(cols[0]), projected),
	}, opt.MakeColSet(cols[1]))
	e.expectRewrite(rule, before, want)

	nearMiss := e.intValues(cols, []int{1, 2, 4})
	otherProjected := e.f.Metadata().AddColumn("q", types.Int)
	nearMissPlan := e.f.ConstructProject(nearMiss, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(cols[0]), otherProjected),
	}, opt.MakeColSet(cols[1]))
	e.expectNoMatch(rule, nearMissPlan)
}
