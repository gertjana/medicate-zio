module Main exposing (main)

import Html exposing (..)
import Html.Attributes exposing (..)

view : a -> Html msg
view _ =
    div [ class "jumbotron" ]
        [ h1 [] [ text "Medicate!" ]
        , p []
            [ text "is an application to maintain your stock of medicine, take doses, add stock and get reminded when you're about to run out. "
            ]
        ]

main : Html msg
main = 
    view "dummy model"