package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestEliminateProject(t *testing.T) {
	rule := loadRule(t, "EliminateProject")
	e := newEnv(t)
	u, uc := e.scan("u", 2)
	before := e.f.ConstructProject(u, memo.EmptyProjectionsExpr, opt.MakeColSet(uc[0], uc[1]))
	e.expectRewrite(rule, before, u)

	wide, wc := e.scan("t", 3)
	e.expectNoMatch(rule, e.f.ConstructProject(wide, memo.EmptyProjectionsExpr, opt.MakeColSet(wc[0], wc[1])))

	computed := e.f.Metadata().AddColumn("k", types.Int)
	items := memo.ProjectionsExpr{{Element: e.num(1), Col: computed}}
	e.expectNoMatch(rule, e.f.ConstructProject(u, items, opt.MakeColSet(uc[0], uc[1])))
}
