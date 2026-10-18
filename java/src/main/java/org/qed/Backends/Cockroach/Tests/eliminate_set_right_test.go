package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestEliminateSetRight(t *testing.T) {
	rule := loadRule(t, "EliminateSetRight")
	e := newEnv(t)
	r, rc := e.scan("t", 3)
	rightSel := e.f.ConstructProject(r, nil, opt.MakeColSet(rc...))
	leftCols := opt.ColList{rc[2], rc[0], rc[1]}
	emptyCols := opt.ColList{
		e.f.Metadata().AddColumn("e0", types.Int),
		e.f.Metadata().AddColumn("e1", types.Int),
		e.f.Metadata().AddColumn("e2", types.Int),
	}
	empty := e.empty(emptyCols...)
	outCols := opt.ColList{
		e.f.Metadata().AddColumn("o0", types.Int),
		e.f.Metadata().AddColumn("o1", types.Int),
		e.f.Metadata().AddColumn("o2", types.Int),
	}
	before := e.f.ConstructUnionAll(rightSel, empty, &memo.SetPrivate{
		LeftCols: leftCols, RightCols: emptyCols, OutCols: outCols,
	})
	want := e.f.ConstructProject(rightSel, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(rc[2]), outCols[0]),
		e.f.ConstructProjectionsItem(e.v(rc[0]), outCols[1]),
		e.f.ConstructProjectionsItem(e.v(rc[1]), outCols[2]),
	}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	nonEmpty := e.intValues(emptyCols, []int{1, 2, 3})
	e.expectNoMatch(rule, e.f.ConstructUnionAll(rightSel, nonEmpty, &memo.SetPrivate{
		LeftCols: leftCols, RightCols: emptyCols, OutCols: outCols,
	}))
	e.expectNoMatch(rule, e.f.ConstructUnionAll(empty, rightSel, &memo.SetPrivate{
		LeftCols: emptyCols, RightCols: leftCols, OutCols: outCols,
	}))
}
